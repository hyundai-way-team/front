// WAY 동승자 웹 — Pleos 문자의 참여 링크로 연다. 앱 설치·로그인 없이 이 페이지에서 끝난다(기획 3-4).
// 흐름: P-01 링크 첫 화면 → P-02 위치 공유 허용 → (차 상태에 따라) P-03 대기 · P-04 곧 도착 · P-05 대기 타이머 → P-06 종료
// 위치를 공유 중이면 P-03·P-04 의 '경로 안내'로 P-04a(내 위치 → 픽업 위치 걷는 길)를 연다. 길은 서버가 계산해 view.walk 로 보낸다.
import { createMap, distanceMeters, offsetMeters } from "/web/map.js";

const root = document.getElementById("app");
const token = decodeURIComponent(location.pathname.split("/")[2] ?? "");
// ?demo — 시연용 가상 위치: 픽업 위치 북쪽 130m 집에서 기다리다 "곧 도착해요"를 받으면 걸어 나온다
const demo = new URLSearchParams(location.search).has("demo");
const introKey = `way:${token}:intro`;

let view = null; // 서버가 보낸 최신 화면 상태
let clockOffset = 0; // 서버 시계 - 내 시계(대기 타이머를 차 화면과 맞춘다)
let step = "intro"; // intro → consent → live
let routeOpen = history.state?.wayRoute === true; // P-04a 경로 안내를 열었는지(뒤로 가기로 닫힌다)
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

/** 걷는 길 — '도보 3분 | 180m' (1000m 부터 km 소수 한 자리) */
function walkText(w) {
  const min = Math.max(1, Math.ceil(w.seconds / 60));
  const dist = w.meters >= 1000 ? `${(w.meters / 1000).toFixed(1)}km` : `${w.meters}m`;
  return `도보 ${min}분 | ${dist}`;
}

/** 대기 시간 — 60 → '1분', 90 → '1분 30초' */
function waitText(sec) {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  if (m === 0) return `${s}초`;
  return s === 0 ? `${m}분` : `${m}분 ${s}초`;
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

const icon = (name, size = 20) => h("img", { src: `/web/icons/${name}.svg`, alt: "", width: size, height: size });
const cardHead = (name, text) => h("div", { class: "card-head" }, icon(name), h("span", {}, text));
const logo = () => h("p", { class: "logo" }, "WAY");
const set = (node, text) => {
  if (node.textContent !== text) node.textContent = text;
};
const show = (node, on) => {
  node.hidden = !on;
};

/** 지도에 넘길 점들. route: 걷는 길을 그릴지 · car: 차를 보일지 */
function mapView(v, { route = false, car = true } = {}) {
  return {
    pickup: v.pickup.location,
    car: car ? (v.car?.location ?? null) : null,
    me: v.me ?? null,
    route: route ? (v.walk?.path ?? null) : null,
  };
}

// 위치를 공유 중이고, 지금 자리에서 길을 못 찾은 게 아닐 때(서버 walkFailed — 공개 경로 서버가 못 찾음·3 km 넘게 멂)
const canRoute = (v) => v.sharing === "SHARING" && !v.walkFailed && (v.phase === "EN_ROUTE" || v.phase === "ARRIVING_SOON");

// P-04a 경로 안내 — 휴대폰 '뒤로'로도 닫히게 기록을 하나 쌓는다
function openRoute() {
  routeOpen = true;
  history.pushState({ wayRoute: true }, "");
  render();
}

function closeRoute() {
  if (history.state?.wayRoute) {
    history.back(); // popstate 가 닫는다
    return;
  }
  routeOpen = false;
  render();
}

window.addEventListener("popstate", () => {
  routeOpen = history.state?.wayRoute === true;
  render();
});

// ── 화면 조각

/** '탑승 차량을 확인해 주세요' 카드(P-01·P-04·P-04a·P-05) */
function vehicleCard() {
  const plate = h("p", { class: "plate" });
  const desc = h("p", { class: "vehicle-desc" });
  const el = h("div", { class: "card vehicle-card" }, cardHead("car", "탑승 차량을 확인해 주세요"), h("div", { class: "vehicle-row" }, h("div", { class: "car-tile" }), h("div", { class: "stack grow-x" }, plate, desc)));
  return {
    el,
    update(v) {
      set(plate, v.vehicle.plateNumber);
      set(desc, v.vehicle.description);
    },
  };
}

/** 지도 카드 아래 '픽업 위치를 확인해 주세요'(P-01·P-04·P-05 길 없을 때) */
function pickupPlace() {
  const title = h("p", { class: "place-title" });
  const detail = h("p", { class: "s-regular muted" });
  const el = h("div", { class: "place" }, cardHead("navigate", "픽업 위치를 확인해 주세요"), h("div", { class: "stack", style: "gap:2px" }, title, detail));
  return {
    el,
    update(v) {
      set(title, v.pickup.title);
      set(detail, v.pickup.detail);
    },
  };
}

/** '경로 안내' 버튼 — 위치를 공유 중일 때만(내 위치가 있어야 길을 낸다) */
function routeButton() {
  return h("button", { class: "btn outline-primary", type: "button", onclick: openRoute, hidden: "" }, icon("arrow", 24), "경로 안내");
}

/** '이 경로를 따라 이동해 주세요' 설명 두 줄 */
const followLines = (name) => [`${name} 님의 차량 화면에도 동일한 경로가 표시돼요.`, "약속 장소까지 같은 경로를 확인하며 이동할 수 있어요."];

function setLines(node, lines) {
  const key = lines.join("\n");
  if (node.dataset.text === key) return;
  node.dataset.text = key;
  node.replaceChildren(...lines.flatMap((l, i) => (i ? [h("br"), l] : [l])));
}

/** 도착 예정 카드(P-03 · P-04) — 오른쪽 100×100 원은 디자인 그대로(그래픽 자리) */
function etaCard({ head, accent = false }) {
  const time = h("p", { class: "time" });
  const left = h("p", { class: "left" });
  const el = h("div", { class: `eta-card${accent ? " accent" : ""}` }, h("div", { class: "stack grow-x", style: "gap:8px" }, head, h("div", { class: "stack", style: "gap:2px" }, time, left)), h("div", { class: "eta-graphic", "aria-hidden": "true" }));
  return { el, time, left };
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
    const eta = h("span", { class: "value" });
    const vehicle = vehicleCard();
    const place = pickupPlace();
    const mapBox = h("div", { class: "map h180" });
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
        h("div", { class: "stack", style: "gap:16px" }, h("div", { class: "stack", style: "gap:4px" }, title, sub), h("div", { class: "eta-box" }, h("span", { class: "label" }, "도착 예정 시간"), eta)),
        h("div", { class: "stack", style: "gap:16px" }, vehicle.el, h("div", { class: "card map-card" }, mapBox, place.el)),
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
        vehicle.update(v);
        place.update(v);
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
        { class: "stack consent-body", style: "gap:40px" },
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

  // P-03 대기 — 도착 예정 시간을 크게, 바뀌었으면 위에 노란 배너. 위치 공유 중이면 '경로 안내'
  wait() {
    const banner = h("div", { class: "banner-changed", hidden: "" }, icon("info-warning"), h("span", {}, "도착 시간이 변경되었어요"));
    const who = h("p", { class: "who" });
    const card = etaCard({ head: who });
    const mapBox = h("div", { class: "map h180" });
    const car = h("span", { class: "v" });
    const spot = h("span", { class: "v" });
    const route = routeButton();
    let map = null;

    const el = h(
      "section",
      { class: "screen" },
      logo(),
      banner,
      h(
        "div",
        { class: "stack", style: "gap:20px" },
        card.el,
        h("div", { class: "card map-card r14" }, mapBox, h("div", { class: "rows" }, h("div", { class: "row" }, h("span", { class: "k" }, "차량"), car), h("div", { class: "row" }, h("span", { class: "k" }, "픽업 위치"), spot))),
        route,
      ),
      h("div", { class: "grow" }),
      h("p", { class: "note" }, "도착 5분 전에 픽업 안내 문자를 보내드려요.", h("br"), "문자를 받으면 픽업 장소로 이동해 주세요."),
    );

    return {
      el,
      update(v) {
        map ??= createMap(mapBox, { carLabel: `${v.driverCallName} 님 차` });
        show(banner, !!v.etaChange);
        set(who, `${v.driverCallName} 님의 도착 예정`);
        set(card.time, hhmm(v.arrivalAt));
        const sec = secondsLeft(v);
        set(card.left, sec < 60 ? "곧 도착해요" : `${minutesText(sec)} 남았어요`);
        set(car, `${v.vehicle.plateNumber} | ${v.vehicle.description}`);
        set(spot, v.pickup.title);
        show(route, canRoute(v));
        map.update(mapView(v));
      },
      destroy: () => map?.destroy(),
    };
  },

  // P-04 곧 도착해요 — 탑승 차량 · 픽업 위치 확인, 위치 공유 중이면 '경로 안내'
  soon() {
    const card = etaCard({ head: h("div", { class: "eta-head" }, icon("dot", 24), h("span", {}, "곧 도착해요")), accent: true });
    const vehicle = vehicleCard();
    const place = pickupPlace();
    const mapBox = h("div", { class: "map h180" });
    const route = routeButton();
    let map = null;

    const el = h(
      "section",
      { class: "screen" },
      logo(),
      h("div", { class: "stack", style: "gap:20px" }, card.el, h("div", { class: "stack", style: "gap:16px" }, vehicle.el, h("div", { class: "card map-card" }, mapBox, place.el)), route),
    );

    return {
      el,
      update(v) {
        map ??= createMap(mapBox, { carLabel: `${v.driverCallName} 님 차` });
        set(card.time, hhmm(v.arrivalAt));
        const sec = secondsLeft(v);
        set(card.left, sec < 60 ? "곧 픽업 위치에 도착해요" : `${minutesText(sec)} 뒤 픽업 위치에 도착해요`);
        vehicle.update(v);
        place.update(v);
        show(route, canRoute(v));
        map.update(mapView(v));
      },
      destroy: () => map?.destroy(),
    };
  },

  // P-04a 경로 안내 — 내 위치에서 픽업 위치까지 가장 빠른 걷는 길(서버 계산). '<'·뒤로 가기로 돌아간다
  route() {
    const back = h("button", { class: "back", type: "button", "aria-label": "뒤로", onclick: closeRoute }, icon("back", 32));
    const vehicle = vehicleCard();
    const mapBox = h("div", { class: "map h270" });
    const walkLabel = h("p", { class: "s-regular muted" }, "현재 위치에서 가는 가장 빠른 길이에요");
    const walkMain = h("p", { class: "walk-main" });
    const pending = h("p", { class: "walk-pending" }, "경로를 찾고 있어요");
    const lines = h("p", { class: "s-regular muted" });
    const guide = h("div", { class: "guide-card" }, icon("info", 24), h("div", { class: "stack grow-x", style: "gap:4px" }, h("p", { class: "m-semibold ink" }, "이 경로를 따라 이동해 주세요"), lines));
    let map = null;

    const el = h(
      "section",
      { class: "screen" },
      h("div", { class: "top-bar" }, back, h("p", { class: "title-m" }, "경로 안내")),
      h(
        "div",
        { class: "stack", style: "gap:16px" },
        vehicle.el,
        h("div", { class: "card map-card" }, mapBox, h("div", { class: "walk-row" }, h("div", { class: "walk-tile" }, icon("footsteps", 24)), h("div", { class: "stack grow-x" }, walkLabel, walkMain, pending))),
        guide,
      ),
    );

    return {
      el,
      update(v) {
        map ??= createMap(mapBox, { carLabel: `${v.driverCallName} 님 차` });
        vehicle.update(v);
        const w = v.walk;
        show(walkLabel, !!w);
        show(walkMain, !!w);
        show(pending, !w);
        show(guide, !!w);
        if (w) set(walkMain, walkText(w));
        setLines(lines, followLines(v.driverCallName));
        map.update(mapView(v, { route: true, car: false }));
      },
      destroy: () => map?.destroy(),
    };
  },

  // P-05 정차 · 대기 타이머 — 차 화면과 같은 1분(서버 시계 기준) + 걷는 길
  timer() {
    const NS = "http://www.w3.org/2000/svg";
    const R = 41.625; // 지름 90 · 두께 6.75(피그마 고리)
    const C = 2 * Math.PI * R;
    const svg = document.createElementNS(NS, "svg");
    svg.setAttribute("viewBox", "0 0 90 90");
    const arc = document.createElementNS(NS, "circle");
    for (const [cls, node] of [["track", document.createElementNS(NS, "circle")], ["arc", arc]]) {
      node.setAttribute("cx", "45");
      node.setAttribute("cy", "45");
      node.setAttribute("r", String(R));
      node.setAttribute("class", cls);
      svg.append(node);
    }
    arc.setAttribute("stroke-dasharray", String(C));
    const sec = h("p", { class: "sec" });
    const main = h("p", { class: "main" });
    const sub = h("p", { class: "s-regular muted" });
    const card = h("div", { class: "timer-card" }, h("div", { class: "ring", role: "timer", "aria-live": "off" }, svg, sec), h("div", { class: "stack grow-x", style: "gap:2px" }, main, sub));

    const mapBox = h("div", { class: "map h180" });
    const chip = h("p", { class: "map-chip", hidden: "" }, "경로를 찾고 있어요");
    mapBox.append(chip);
    const lines = h("p", { class: "s-regular muted" });
    const follow = h("div", { class: "place" }, h("div", { class: "follow-head" }, icon("navigate-24", 24), h("span", {}, "이 경로를 따라 이동해 주세요")), lines);
    const place = pickupPlace(); // 길이 아직 없으면(계산 전 · 위치 미공유) 픽업 위치를 대신 보인다
    const vehicle = vehicleCard();
    let map = null;

    const el = h(
      "section",
      { class: "screen" },
      logo(),
      h(
        "div",
        { class: "stack", style: "gap:20px" },
        h("h1", { class: "title-xl" }, "차량이 픽업 위치에 도착했어요"),
        card,
        h("div", { class: "stack", style: "gap:16px" }, h("div", { class: "card map-card" }, mapBox, follow, place.el), vehicle.el),
      ),
    );

    let tick = null;
    function draw() {
      const t = view?.timer;
      if (!t) return;
      const remaining = Math.max(0, t.endsAt - serverNow());
      const s = Math.ceil(remaining / 1000);
      set(sec, `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`);
      arc.setAttribute("stroke-dashoffset", String(C * (1 - remaining / (t.waitSeconds * 1000))));
      arc.style.visibility = remaining === 0 ? "hidden" : "";
      card.classList.toggle("over", remaining === 0);
      set(main, remaining === 0 ? "대기 시간이 지났어요" : `${waitText(t.waitSeconds)} 안에 탑승해 주세요`);
    }

    return {
      el,
      update(v) {
        map ??= createMap(mapBox, { carLabel: `${v.driverCallName} 님 차` });
        set(sub, `${v.driverCallName} 님 차 화면에도 같은 타이머가 떠요`);
        const w = v.walk;
        show(follow, !!w);
        show(place.el, !w);
        show(chip, !w && v.sharing === "SHARING" && !v.walkFailed);
        setLines(lines, followLines(v.driverCallName));
        place.update(v);
        vehicle.update(v);
        map.update(mapView(v, { route: true }));
        draw();
        tick ??= setInterval(draw, 200);
      },
      destroy() {
        clearInterval(tick);
        map?.destroy();
      },
    };
  },

  // P-06 종료 — 위치 공유 종료 · 링크 만료
  done() {
    const title = h("p", { class: "title-xl center" });
    const door = h("p", { class: "note" }, "차량의 도어 상태를 통해 탑승을 확인하면", h("br"), "화면이 자동으로 전환돼요.");
    const el = h(
      "section",
      { class: "screen done" },
      logo(),
      h("div", { class: "done-top" }),
      h(
        "div",
        { class: "stack", style: "gap:32px" },
        h("div", { class: "stack", style: "gap:16px;align-items:center" }, h("div", { class: "status-icon solid" }, h("img", { src: "/web/icons/check-white.svg", alt: "" })), title),
        h("div", { class: "stack", style: "gap:16px" }, h("div", { class: "done-box" }, "위치 공유가 종료되었어요.", h("br"), "이 링크는 더 이상 사용할 수 없어요."), door),
      ),
      h("div", { class: "done-bottom" }),
    );
    return {
      el,
      update(v) {
        const boarded = v.phase === "BOARDED";
        set(title, boarded ? "탑승이 완료되었어요" : "픽업이 끝났어요");
        show(door, boarded);
      },
    };
  },
};

function screenKey() {
  if (!view) return "loading";
  if (isEnded()) return "done";
  if (step !== "live") return step;
  if (view.phase === "ARRIVED") return "timer";
  if (routeOpen && canRoute(view)) return "route";
  return view.phase === "ARRIVING_SOON" ? "soon" : "wait";
}

function render() {
  const key = screenKey();
  // P-04a 를 연 채 정차·종료·길 없음으로 화면이 바뀌면 쌓아 둔 기록도 걷는다 — 안 그러면 '뒤로'가 한 번 헛돈다
  if (current?.key === "route" && key !== "route" && history.state?.wayRoute) {
    routeOpen = false;
    history.back();
  }
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
