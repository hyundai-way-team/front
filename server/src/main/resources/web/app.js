// WAY 동승자 웹 — Pleos 문자의 참여 링크로 연다. 앱 설치·로그인 없이 이 페이지에서 끝난다(기획 3-4).
// 흐름: P-01 링크 첫 화면 → P-02 위치 공유 허용 → (차 상태에 따라) P-03 대기 · P-04 곧 도착 · P-05 대기 타이머 → P-06 종료
import { createMap, distanceMeters, offsetMeters } from "/web/map.js";

const root = document.getElementById("app");
const token = decodeURIComponent(location.pathname.split("/")[2] ?? "");
// ?demo — 시연용 가상 위치: 픽업 위치 북쪽 130m 집에서 기다리다 "곧 도착해요"를 받으면 걸어 나온다
const demo = new URLSearchParams(location.search).has("demo");
const introKey = `way:${token}:intro`;

let view = null; // 서버가 보낸 최신 화면 상태
let clockOffset = 0; // 서버 시계 - 내 시계(대기 타이머를 차 화면과 맞춘다)
let step = "intro"; // intro → consent → live
let current = null; // 지금 화면 { key, el, update, destroy }
let failure = null; // 링크를 못 열었을 때 문구

// ── 서버

async function api(path, body) {
  const res = await fetch(`/api/p/${encodeURIComponent(token)}${path}`, {
    method: body === undefined ? "GET" : "POST",
    headers: body === undefined ? {} : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    const error = new Error(`${res.status}`);
    error.status = res.status;
    throw error;
  }
  return res.status === 204 ? null : res.json();
}

let socket = null;
function connectLive() {
  const scheme = location.protocol === "https:" ? "wss" : "ws";
  socket = new WebSocket(`${scheme}://${location.host}/api/p/${encodeURIComponent(token)}/live`);
  socket.onmessage = (e) => receive(JSON.parse(e.data));
  socket.onclose = () => {
    socket = null;
    if (!isEnded()) setTimeout(connectLive, 2000); // 끊기면 다시 — 연결되면 지금 상태부터 다시 온다
  };
}

function receive(v) {
  clockOffset = v.serverNow - Date.now();
  view = v;
  if (isEnded()) {
    sharing.stop();
    socket?.close();
  } else if (v.sharing === "SHARING") {
    sharing.resume(); // 새로고침해도 공유를 이어 간다
  }
  render();
}

const isEnded = () => view && (view.phase === "BOARDED" || view.phase === "CLOSED");
const serverNow = () => Date.now() + clockOffset;

// ── 내 위치 공유

const sharing = (() => {
  let watchId = null;
  let walker = null;
  let lastSent = 0;
  let lastPos = null;

  async function send(pos) {
    try {
      await api("/location", pos);
    } catch (e) {
      if (e.status === 409 || e.status === 410) stop();
    }
  }

  function onPosition(p) {
    const pos = { lat: p.coords.latitude, lng: p.coords.longitude, accuracyMeters: p.coords.accuracy };
    const now = Date.now();
    // 2초에 한 번, 또는 5m 넘게 움직였을 때만 보낸다
    if (now - lastSent < 2000 && lastPos && distanceMeters(lastPos, pos) < 5) return;
    lastSent = now;
    lastPos = pos;
    send(pos);
  }

  function watch() {
    if (watchId !== null || walker !== null) return;
    if (demo) {
      walker = startWalker();
      return;
    }
    watchId = navigator.geolocation.watchPosition(onPosition, () => {}, { enableHighAccuracy: true, maximumAge: 5000 });
  }

  // 시연용: 집(픽업 위치 북쪽 130m)에서 기다리다 '곧 도착'·정차 때 1.3m/s 로 걸어온다
  function startWalker() {
    const pickup = view.pickup.location;
    let here = offsetMeters(pickup, 130, 0);
    send(here);
    return setInterval(() => {
      if (view.phase === "ARRIVING_SOON" || view.phase === "ARRIVED") {
        const left = distanceMeters(here, pickup);
        const k = left <= 1.3 ? 1 : 1.3 / left;
        here = { lat: here.lat + (pickup.lat - here.lat) * k, lng: here.lng + (pickup.lng - here.lng) * k };
      }
      send(here);
    }, 1000);
  }

  function stop() {
    if (watchId !== null) navigator.geolocation.clearWatch(watchId);
    if (walker !== null) clearInterval(walker);
    watchId = null;
    walker = null;
  }

  /** P-02 '위치 공유 허용' — 브라우저 위치 권한을 먼저 받고 서버에 알린다. 실패하면 이유를 돌려준다. */
  async function allow() {
    if (!demo) {
      if (!("geolocation" in navigator) || !window.isSecureContext) {
        return "이 주소에서는 브라우저가 위치를 막아요. https 주소에서 열어 주세요.";
      }
      try {
        const p = await new Promise((ok, fail) =>
          navigator.geolocation.getCurrentPosition(ok, fail, { enableHighAccuracy: true, timeout: 15000 }),
        );
        await api("/sharing", { allow: true });
        onPosition(p);
      } catch (e) {
        if (e.code === 1) return "브라우저가 위치 권한을 막았어요. 설정에서 허용하거나 '허용하지 않고 계속'을 눌러 주세요.";
        if (e.code === 2 || e.code === 3) return "지금 위치를 찾지 못했어요. 잠시 뒤 다시 눌러 주세요.";
        throw e;
      }
    } else {
      await api("/sharing", { allow: true });
    }
    watch();
    return null;
  }

  return { allow, resume: watch, stop };
})();

// ── 글자

function hhmm(ms) {
  const parts = new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit", hourCycle: "h23", timeZone: "Asia/Seoul" }).formatToParts(new Date(ms));
  const get = (t) => parts.find((p) => p.type === t)?.value ?? "00";
  return `${get("hour")}:${get("minute")}`;
}

function secondsLeft(v) {
  return v.car ? v.car.remainingSeconds : Math.max(0, Math.round((v.arrivalAt - serverNow()) / 1000));
}

function minutesText(sec) {
  return `약 ${Math.max(1, Math.round(sec / 60))}분`;
}

// ── DOM

function h(tag, attrs = {}, ...children) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null) continue;
    if (k === "class") e.className = v;
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else e.setAttribute(k, v);
  }
  for (const c of children.flat()) if (c !== null && c !== undefined && c !== false) e.append(c instanceof Node ? c : String(c));
  return e;
}

const icon = (name) => h("img", { src: `/web/icons/${name}.svg`, alt: "", width: 20, height: 20 });
const cardHead = (name, text) => h("div", { class: "card-head" }, icon(name), h("span", {}, text));
const logo = () => h("p", { class: "logo" }, "WAY");
const set = (node, text) => {
  if (node.textContent !== text) node.textContent = text;
};

function mapView(v, meLabel) {
  return { pickup: v.pickup.location, car: v.car?.location ?? null, me: v.me ?? null, meLabel };
}

// ── 화면

const screens = {
  loading() {
    const el = h("section", { class: "screen" }, logo(), h("p", { class: "m-regular muted" }, failure ?? "불러오는 중…"));
    return { el };
  },

  // P-01 링크 첫 화면 — 누가 오는지 · 차량 번호 · 픽업 위치 · 실시간 도착 예정
  intro() {
    const title = h("p", { class: "title-xl" });
    const sub = h("p", { class: "m-regular muted" });
    const eta = h("span");
    const refresh = h("button", { class: "icon-btn", type: "button", "aria-label": "도착 예정 시간 새로 보기" }, h("img", { src: "/web/icons/refresh.svg", alt: "" }));
    refresh.addEventListener("click", async () => {
      refresh.classList.remove("spin");
      void refresh.offsetWidth; // 애니메이션 다시 시작
      refresh.classList.add("spin");
      try {
        receive(await api(""));
      } catch {
        /* 실시간 연결로 곧 다시 온다 */
      }
    });
    const plate = h("p", { class: "plate" });
    const desc = h("p", { class: "vehicle-desc" });
    const mapBox = h("div", { class: "map h180" });
    const spotTitle = h("p", { class: "place-title" });
    const spotDetail = h("p", { class: "s-regular muted" });
    const next = h("button", { class: "btn primary", type: "button" }, "다음");
    next.addEventListener("click", () => {
      sessionStorage.setItem(introKey, "1");
      step = view.sharing === "NOT_YET" ? "consent" : "live";
      render();
    });
    const map = createMap(mapBox, { carLabel: "차" });

    const el = h(
      "section",
      { class: "screen soft" },
      logo(),
      h(
        "div",
        { class: "stack", style: "gap:20px" },
        h(
          "div",
          { class: "stack", style: "gap:16px" },
          h("div", { class: "stack", style: "gap:4px" }, title, sub),
          h("div", { class: "eta-box" }, h("span", { class: "label" }, "실시간 도착 예정"), h("span", { class: "value" }, eta, refresh)),
        ),
        h(
          "div",
          { class: "stack", style: "gap:16px" },
          h("div", { class: "card vehicle-card" }, cardHead("car", "탑승 차량을 확인해 주세요"), h("div", { class: "vehicle-row" }, h("div", { class: "car-tile" }), h("div", { class: "stack" }, plate, desc))),
          h(
            "div",
            { class: "card map-card" },
            mapBox,
            h("div", { class: "place" }, cardHead("navigate", "픽업 위치를 확인해 주세요"), h("div", { class: "stack", style: "gap:2px" }, spotTitle, spotDetail)),
          ),
        ),
      ),
      h("div", { class: "grow" }),
      h("div", { class: "stack", style: "gap:12px" }, h("p", { class: "caption" }, "앱 설치, 로그인 없이 이 페이지에서 끝나요"), next),
    );

    return {
      el,
      update(v) {
        set(title, `${v.driverCallName} 님이 데리러 가고 있어요`);
        set(sub, `현재 위치에서 ${minutesText(secondsLeft(v))} 소요돼요.`);
        set(eta, hhmm(v.arrivalAt));
        set(plate, v.vehicle.plateNumber);
        set(desc, v.vehicle.description);
        set(spotTitle, v.pickup.title);
        set(spotDetail, v.pickup.detail);
        map.update(mapView(v));
      },
      destroy: () => map.destroy(),
    };
  },

  // P-02 위치 공유 허용 — 허용 직전에 "타면 자동으로 종료돼요"를 보여 준다(기획 3-4 규칙)
  consent() {
    const title = h("p", { class: "title-xl center" });
    const doneLine = h("span");
    const noteLine = h("span");
    const toast = h("p", { class: "toast", role: "alert", hidden: "" });
    const allow = h("button", { class: "btn primary", type: "button" }, "위치 공유 허용");
    const decline = h("button", { class: "btn ghost", type: "button" }, "허용하지 않고 계속");
    allow.addEventListener("click", async () => {
      allow.disabled = decline.disabled = true;
      toast.hidden = true;
      try {
        const problem = await sharing.allow();
        if (problem) {
          toast.textContent = problem;
          toast.hidden = false;
        } else {
          step = "live";
          render();
        }
      } catch {
        toast.textContent = "서버에 닿지 않아요. 잠시 뒤 다시 눌러 주세요.";
        toast.hidden = false;
      } finally {
        allow.disabled = decline.disabled = false;
      }
    });
    decline.addEventListener("click", async () => {
      decline.disabled = true;
      try {
        await api("/sharing", { allow: false });
      } catch {
        /* 실패해도 화면은 넘어간다 — 운전자 화면엔 '링크 확인 전/위치 확인 중' */
      }
      step = "live";
      render();
    });
    const item = (text) => h("li", {}, h("img", { src: "/web/icons/check.svg", alt: "" }), text);

    const el = h(
      "section",
      { class: "screen" },
      logo(),
      h(
        "div",
        { class: "stack", style: "gap:40px" },
        h(
          "div",
          { class: "stack", style: "gap:16px" },
          h("div", { class: "status-icon" }, h("img", { src: "/web/icons/pin.svg", alt: "" })),
          h("div", { class: "stack", style: "gap:4px" }, title, h("p", { class: "m-regular muted center" }, "전화 없이도 위치를 확인하고 만날 수 있어요.")),
        ),
        h(
          "div",
          { class: "stack", style: "gap:20px" },
          h("div", { class: "checklist" }, h("ul", {}, item("차에 타면 자동으로 종료돼요"), item(doneLine), item("이 링크는 탑승하면 만료돼요"))),
          h("p", { class: "note" }, "허용하지 않아도 차량 위치와 도착 예정 시간은 볼 수 있어요.", h("br"), noteLine),
        ),
      ),
      toast,
      h("div", { class: "grow" }),
      h("div", { class: "stack", style: "gap:12px" }, allow, decline),
    );

    return {
      el,
      update(v) {
        set(title, `내 위치를 ${v.driverCallName} 님과 공유할까요?`);
        set(doneLine, `${v.driverCallName} 님의 차량 화면에만 표시돼요`);
        set(noteLine, `${v.driverCallName} 님 화면에는 “위치 미공유”로 보여요.`);
      },
    };
  },

  // P-03 대기 — 도착 예정 시간을 가장 크게
  wait() {
    const banner = h("div", { class: "banner-warn", hidden: "" });
    const who = h("p", { class: "who" });
    const time = h("p", { class: "time" });
    const left = h("p", { class: "left" });
    const mapBox = h("div", { class: "map h220" });
    const chip = h("div", { class: "map-chip" });
    mapBox.append(chip);
    const car = h("span", { class: "v" });
    const spot = h("span", { class: "v" });
    let map = null;

    const el = h(
      "section",
      { class: "screen wide" },
      logo(),
      h(
        "div",
        { class: "stack", style: "gap:14px" },
        banner,
        h("div", { class: "eta-hero" }, who, time, left),
        mapBox,
        h("div", { class: "rows" }, h("div", { class: "row" }, h("span", { class: "k" }, "차량"), car), h("div", { class: "row" }, h("span", { class: "k" }, "픽업 위치"), spot)),
        h("p", { class: "hint-box" }, "도착 5분 전에 “곧 도착해요” 문자를 보내 드려요.", h("br"), "그때 나오시면 돼요."),
      ),
    );

    return {
      el,
      update(v) {
        map ??= createMap(mapBox, { carLabel: `${v.driverCallName} 님 차` });
        if (v.etaChange) {
          banner.replaceChildren(h("b", {}, "도착 시간이 바뀌었어요"), ` · ${hhmm(v.etaChange.from)} → ${hhmm(v.etaChange.to)} (문자로도 보냈어요)`);
          banner.hidden = false;
        } else banner.hidden = true;
        set(who, `${v.driverCallName} 님 도착 예정`);
        set(time, hhmm(v.arrivalAt));
        const sec = secondsLeft(v);
        set(left, sec < 60 ? "곧 도착해요" : `${minutesText(sec)} 남았어요`);
        set(chip, v.me ? "차 · 나 · 픽업 위치 실시간" : "차 · 픽업 위치 실시간");
        set(car, `${v.vehicle.plateNumber} · ${v.vehicle.description}`);
        set(spot, v.pickup.title);
        map.update(mapView(v));
      },
      destroy: () => map?.destroy(),
    };
  },

  // P-04 곧 도착해요 — 차량 번호를 크게(비슷한 차들 사이에서 바로 찾게)
  soon() {
    const small = h("p", { class: "small" });
    const mapBox = h("div", { class: "map h240" });
    const spotTitle = h("p", { class: "spot-18" });
    const spotDetail = h("p", { class: "s-regular muted" });
    const plate = h("p", { class: "plate-big" });
    const desc = h("p", { class: "desc" });
    let map = null;

    const el = h(
      "section",
      { class: "screen wide" },
      logo(),
      h(
        "div",
        { class: "stack", style: "gap:14px" },
        h("div", { class: "banner-blue" }, h("p", { class: "big" }, "곧 도착해요"), small),
        mapBox,
        h("div", { class: "stack", style: "gap:2px" }, h("p", { class: "label-13" }, "픽업 위치"), spotTitle, spotDetail),
        h("div", { class: "find-car" }, h("div", { class: "car-tile big" }), h("div", { class: "stack" }, h("p", { class: "label-13" }, "이 차를 찾으세요"), plate, desc)),
      ),
    );

    return {
      el,
      update(v) {
        map ??= createMap(mapBox, { carLabel: `${v.driverCallName} 님 차`, showRoute: true });
        const sec = secondsLeft(v);
        set(small, sec < 60 ? "곧 픽업 위치에 도착해요. 지금 나와서 먼저 기다려 주세요." : `${minutesText(sec)} 뒤 픽업 위치에 도착해요. 지금 나와서 먼저 기다려 주세요.`);
        set(spotTitle, v.pickup.title);
        set(spotDetail, v.pickup.detail);
        set(plate, v.vehicle.plateNumber);
        set(desc, v.vehicle.description);
        const meLabel = v.me ? `나 · ${Math.round(distanceMeters(v.me, v.pickup.location))}m` : undefined;
        map.update(mapView(v, meLabel));
      },
      destroy: () => map?.destroy(),
    };
  },

  // P-05 정차 · 대기 타이머 — 차 화면과 같은 1분(서버 시계 기준)
  timer() {
    const title = h("h1", { class: "title-xl", style: "color:var(--ink)" });
    const R = 78;
    const C = 2 * Math.PI * R;
    const arc = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 168 168");
    for (const [stroke, node] of [["var(--ring)", document.createElementNS("http://www.w3.org/2000/svg", "circle")], ["var(--primary-500)", arc]]) {
      node.setAttribute("cx", "84");
      node.setAttribute("cy", "84");
      node.setAttribute("r", String(R));
      node.setAttribute("fill", "none");
      node.setAttribute("stroke-width", "12");
      node.setAttribute("stroke-linecap", "round");
      node.style.stroke = stroke;
      svg.append(node);
    }
    arc.setAttribute("stroke-dasharray", String(C));
    const sec = h("p", { class: "sec" });
    const unit = h("p", { class: "sub" }, "남음");
    const card = h("div", { class: "timer-card" });
    const subLine = h("p", { class: "sub" });
    const standing = h("p", { class: "label-13" });
    const plate = h("p", { class: "plate-big" });
    const desc = h("p", { class: "desc" });
    const footer = h("p", { class: "caption" });
    card.append(h("div", { class: "ring", role: "timer", "aria-live": "off" }, svg, h("div", { class: "center" }, sec, unit)), h("div", { class: "timer-text" }, h("p", { class: "main" }, "1분 안에 타 주세요"), subLine));

    const el = h(
      "section",
      { class: "screen wide" },
      logo(),
      h("div", { class: "stack", style: "gap:16px" }, title, card, h("div", { class: "find-car" }, h("div", { class: "car-tile big" }), h("div", { class: "stack" }, standing, plate, desc))),
      h("div", { class: "grow" }),
      footer,
    );

    let tick = null;
    function draw() {
      const t = view?.timer;
      if (!t) return;
      const remaining = Math.max(0, t.endsAt - serverNow());
      const s = Math.ceil(remaining / 1000);
      set(sec, `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`);
      arc.setAttribute("stroke-dashoffset", String(C * (1 - remaining / (t.waitSeconds * 1000))));
      card.classList.toggle("over", remaining === 0);
      set(unit, remaining === 0 ? "시간이 지났어요" : "남음");
    }

    return {
      el,
      update(v) {
        set(title, `${v.driverCallName} 님 차가 픽업 위치에 도착했어요`);
        set(subLine, `${v.driverCallName} 님 차 화면에도 같은 타이머가 떠요`);
        set(standing, `${v.pickup.title}에 서 있어요`);
        set(plate, v.vehicle.plateNumber);
        set(desc, v.vehicle.description);
        set(footer, `15초 남았는데 아직 타지 않았으면 ${v.driverCallName} 님이 전화를 걸 수 있어요`);
        draw();
        tick ??= setInterval(draw, 200);
      },
      destroy: () => clearInterval(tick),
    };
  },

  // P-06 종료 — 위치 공유 종료 · 링크 만료
  done() {
    const title = h("p", { class: "done-title" });
    const el = h(
      "section",
      { class: "screen done" },
      h("div", { class: "done-icon" }, h("img", { src: "/web/icons/check-white.svg", alt: "" })),
      title,
      h("div", { class: "done-box" }, h("p", {}, "위치 공유가 종료됐어요"), h("p", {}, "이 링크는 만료됐어요")),
    );
    return {
      el,
      update(v) {
        set(title, v.phase === "BOARDED" ? "탑승했어요" : "픽업이 끝났어요");
      },
    };
  },
};

function screenKey() {
  if (!view) return "loading";
  if (isEnded()) return "done";
  if (step !== "live") return step;
  return { ARRIVING_SOON: "soon", ARRIVED: "timer" }[view.phase] ?? "wait";
}

function render() {
  const key = screenKey();
  if (current?.key !== key) {
    current?.destroy?.();
    current = { key, ...screens[key]() };
    root.replaceChildren(current.el);
    if (demo) root.append(h("div", { class: "demo-badge" }, "시연 · 가상 위치"));
    window.scrollTo(0, 0);
  }
  if (view) current.update?.(view);
}

async function start() {
  render();
  try {
    const v = await api("");
    const sawIntro = sessionStorage.getItem(introKey) === "1";
    step = sawIntro ? (v.sharing === "NOT_YET" ? "consent" : "live") : "intro";
    receive(v);
    if (!isEnded()) {
      api("/open", {}).catch(() => {}); // 링크 확인 — 운전자에게 "OO님이 픽업 위치를 확인했어요"
      connectLive();
    }
  } catch (e) {
    failure = e.status === 404 ? "링크를 찾을 수 없어요. 문자의 링크를 다시 눌러 주세요." : "서버에 닿지 않아요. 잠시 뒤 다시 열어 주세요.";
    current = null;
    render();
  }
}

start();
