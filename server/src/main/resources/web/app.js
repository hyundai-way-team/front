// WAY 동승자 웹 — Pleos 문자의 참여 링크로 연다. 앱 설치·로그인 없이 이 페이지에서 끝난다(기획 3-4).
// 흐름: P-01 링크 첫 화면 → P-02 위치 공유 허용 → (차 상태에 따라) P-03 대기 · P-04 곧 도착 · P-05 대기 타이머 → P-06 종료
// 위치를 공유 중이면 P-03 · P-04 · P-05 의 아래 시트를 끌어 올려 '경로 안내'로 P-04a(내 위치 → 픽업 위치 걷는 길)를 연다.
// 길은 서버가 계산해 view.walk 로 보낸다.
// P-03 · P-04 · P-04a · P-05 는 지도가 화면을 채우고 카드·시트가 위에 뜬다(와이어프레임 v2 개정). 지도는 손으로 움직일 수 있다(map.js).
import { createMap, distanceMeters, offsetMeters } from "/web/map.js";
import { timerTone } from "/web/timer-color.js";

const root = document.getElementById("app");
const token = decodeURIComponent(location.pathname.split("/")[2] ?? "");
// ?demo — 시연용 가상 위치: 픽업 위치 북쪽 130m 집에서 기다리다 "곧 도착해요"를 받으면 걸어 나온다
const demo = new URLSearchParams(location.search).has("demo");
const introKey = `way:${token}:intro`;

let view = null; // 서버가 보낸 최신 화면 상태
let clockOffset = 0; // 서버 시계 - 내 시계(대기 타이머를 차 화면과 맞춘다)
let step = "intro"; // intro → consent → live
let routeOpen = history.state?.wayRoute === true; // P-04a 경로 안내를 열었는지(뒤로 가기로 닫힌다)
let routeFrom = history.state?.wayRouteFrom ?? null; // 어디서 열었는지 — "ARRIVED"(P-05) · "BEFORE"(P-03 · P-04)
let current = null; // 지금 화면 { key, el, update, destroy }
let seenEtaChange = null; // '도착 시간이 변경되었어요'를 이미 보인 변경(전→후) — 화면을 다시 만들어도 같은 변경은 다시 띄우지 않는다
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
/** P-01 카드 머리글(Label/Medium · Primary-500) — 피그마 개정: 앞 아이콘 없음 */
const cardHead = (text) => h("p", { class: "card-head" }, text);
const logo = () => h("p", { class: "logo" }, "WAY");
/** P-01 로고(피그마 477:3378): WAY 워드마크 + WHERE ARE YOU */
const brandLogo = () =>
  h("div", { class: "brand-logo", role: "img", "aria-label": "WAY · Where are you" }, h("img", { class: "way", src: "/web/icons/logo-way.svg", alt: "" }), h("img", { class: "where", src: "/web/icons/logo-where.svg", alt: "" }));
const set = (node, text) => {
  if (node.textContent !== text) node.textContent = text;
};
const show = (node, on) => {
  node.hidden = !on;
};

/** 지도에 넘길 점들. route: 걷는 길을 그릴지 · car: 차(와 차가 오는 파란 길)를 보일지 */
function mapView(v, { route = false, car = true } = {}) {
  return {
    pickup: v.pickup.location,
    car: car ? (v.car?.location ?? null) : null,
    me: v.me ?? null,
    route: route ? (v.walk?.path ?? null) : null,
    // 서버가 차가 오는 중(P-01·P-03·P-04)에만 보낸다 — 정차(P-05) 뒤엔 없음
    carRoute: car ? (v.carRoute ?? null) : null,
  };
}

// 위치를 공유 중이고, 지금 자리에서 길을 못 찾은 게 아닐 때(서버 walkFailed — 공개 경로 서버가 못 찾음·3 km 넘게 멂).
// 오는 중(P-03) · 곧 도착(P-04) · 정차(P-05) 모두 — 시트를 펼치면 '경로 안내'가 보인다
const canRoute = (v) => v.sharing === "SHARING" && !v.walkFailed && (v.phase === "EN_ROUTE" || v.phase === "ARRIVING_SOON" || v.phase === "ARRIVED");

// P-04a 경로 안내 — 휴대폰 '뒤로'로도 닫히게 기록을 하나 쌓는다. 어디서 열었는지도 남긴다:
// 차가 오는 중에 연 것은 차가 도착하면 닫고 P-05(1분 타이머)로, P-05 에서 스스로 연 것은 열린 채로 둔다
function openRoute() {
  routeOpen = true;
  routeFrom = view?.phase === "ARRIVED" ? "ARRIVED" : "BEFORE";
  history.pushState({ wayRoute: true, wayRouteFrom: routeFrom }, "");
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
  routeFrom = history.state?.wayRouteFrom ?? null;
  render();
});

// ── 화면 조각

/** '탑승 차량을 확인해 주세요' 카드(P-01) */
function vehicleCard() {
  const plate = h("p", { class: "plate" });
  const desc = h("p", { class: "vehicle-desc" });
  const el = h("div", { class: "card vehicle-card" }, cardHead("탑승 차량을 확인해 주세요"), h("div", { class: "vehicle-row" }, h("div", { class: "car-tile" }), h("div", { class: "stack grow-x" }, plate, desc)));
  return {
    el,
    update(v) {
      set(plate, v.vehicle.plateNumber);
      set(desc, v.vehicle.description);
    },
  };
}

/** 지도 카드 아래 '픽업 위치를 확인해 주세요'(P-01) */
function pickupPlace() {
  const title = h("p", { class: "place-title" });
  const detail = h("p", { class: "s-regular muted" });
  const el = h("div", { class: "place" }, cardHead("픽업 위치를 확인해 주세요"), h("div", { class: "stack", style: "gap:2px" }, title, detail));
  return {
    el,
    update(v) {
      set(title, v.pickup.title);
      set(detail, v.pickup.detail);
    },
  };
}

/** '경로 안내' 버튼 — 펼친 시트 안(P-03 · P-04 · P-05). 위치를 공유 중일 때만 시트가 펼쳐진다(내 위치가 있어야 길을 낸다) */
function routeButton() {
  return h("button", { class: "btn outline-primary", type: "button", onclick: openRoute }, icon("arrow", 24), "경로 안내");
}

/** '이 경로를 따라 이동해 주세요' 설명 두 줄 */
const followLines = (name) => [`${name} 님의 차량 화면에도 동일한 경로가 표시돼요.`, "약속 장소까지 같은 경로를 확인하며 이동할 수 있어요."];

function setLines(node, lines) {
  const key = lines.join("\n");
  if (node.dataset.text === key) return;
  node.dataset.text = key;
  node.replaceChildren(...lines.flatMap((l, i) => (i ? [h("br"), l] : [l])));
}

/**
 * 도착 예정 카드(P-03 · P-04) — 지도 위에 뜨는 반투명 카드. 오른쪽엔 피그마 그림(art: p03 차가 오는 길 · p04 차와 핀)이
 * 반투명 바탕 위 · 글자 아래에 깔린다(카드 모서리에서 잘림).
 */
function etaCard({ head, accent = false, art }) {
  const time = h("p", { class: "time" });
  const left = h("p", { class: "left" });
  const el = h(
    "div",
    { class: `eta-card glass${accent ? " accent" : ""}` },
    h("img", { class: `eta-art ${art}`, src: `/web/icons/eta-${art}.webp`, alt: "", "aria-hidden": "true", decoding: "async" }),
    h("div", { class: "stack grow-x eta-text", style: "gap:4px" }, head, h("div", { class: "stack" }, time, left)),
  );
  return { el, time, left };
}

/** 아래 시트의 '차량 · 픽업 위치' 두 줄(P-03 · P-04 · P-04a · P-05) */
function carRows() {
  const car = h("span", { class: "v" });
  const spot = h("span", { class: "v" });
  const el = h("div", { class: "rows" }, h("div", { class: "row" }, h("span", { class: "k" }, "차량"), car), h("div", { class: "row" }, h("span", { class: "k" }, "픽업 위치"), spot));
  return {
    el,
    update(v) {
      set(car, `${v.vehicle.plateNumber} | ${v.vehicle.description}`);
      set(spot, v.pickup.title);
    },
  };
}

let sheetSeq = 0;

/**
 * 끌어 올리고 내리는 아래 시트. 접힘 = 손잡이 + body, 펼침 = 그 아래 more(경로 안내 버튼)까지.
 * - 손잡이 · 시트 어디든 손가락/마우스로 끌면 시트가 따라오고, 놓으면 가까운 쪽으로 붙는다(빠르게 튕기면 그 방향).
 * - 손잡이를 누르거나(키보드 Enter · Space 포함) 하면 접기 · 펼치기.
 * - 펼칠 게 없으면(setExpandable(false) — 위치 미공유 · 길 없음 · P-04a) 끌어도 살짝 늘었다 제자리로 돌아온다.
 * - 시트 위 손가락 동작은 시트가 먹는다(touch-action: none) — 지도 끌기와 엉키지 않는다.
 * 처음엔 접힌 채로 — 화면이 바뀌면 시트도 새로 만들어 접힌 채 시작한다.
 */
function bottomSheet({ body, more = [] }) {
  const id = `sheet-more-${++sheetSeq}`;
  const handle = h("button", { class: "sheet-handle", type: "button", "aria-controls": id, "aria-expanded": "false", "aria-label": "경로 안내 펼치기" });
  const moreIn = h("div", { class: "sheet-more-in" }, ...more);
  const moreEl = h("div", { class: "sheet-more", id, "aria-hidden": "true" }, moreIn);
  moreEl.inert = true;
  // 펼칠 게 생기기 전(setExpandable(true))엔 손잡이를 키보드 · 화면 읽기에서 뺀다 — 끌면 살짝 늘었다 돌아올 뿐
  handle.tabIndex = -1;
  handle.setAttribute("aria-hidden", "true");
  const el = h("div", { class: "sheet" }, handle, h("div", { class: "sheet-body" }, ...body), moreEl);

  let open = false;
  let expandable = false;
  let drag = null;
  let draggedAt = 0; // 끌기가 끝난 시각 — 바로 뒤 따라오는 click 은 토글로 치지 않는다
  let unclip = null; // 펼쳐서 붙은 뒤엔 버튼 초점 테두리가 잘리지 않게 넘침을 연다

  const fullHeight = () => (expandable ? moreIn.offsetHeight : 0);
  const rubber = (px) => 24 * (1 - Math.exp(-px / 80)); // 끝을 넘어 끌면 점점 덜 따라온다(최대 24)

  function paint(height, down = 0) {
    const full = fullHeight();
    clearTimeout(unclip);
    moreEl.style.overflow = "";
    moreEl.style.height = `${height}px`;
    moreEl.style.opacity = full ? String(Math.min(1, height / full)) : "0";
    el.style.transform = down ? `translateY(${down}px)` : "";
  }

  function settle(toOpen) {
    open = toOpen && expandable;
    el.classList.add("settling");
    paint(open ? fullHeight() : 0);
    if (open) unclip = setTimeout(() => (moreEl.style.overflow = "visible"), 240);
    moreEl.inert = !open;
    moreEl.setAttribute("aria-hidden", String(!open));
    handle.setAttribute("aria-expanded", String(open));
    handle.setAttribute("aria-label", open ? "경로 안내 접기" : "경로 안내 펼치기");
  }

  handle.addEventListener("click", () => {
    if (!expandable || performance.now() - draggedAt < 300) return;
    settle(!open);
  });

  el.addEventListener("pointerdown", (e) => {
    if (e.button !== 0 || drag) return;
    drag = { id: e.pointerId, y0: e.clientY, h0: moreEl.offsetHeight, moved: false, pts: [[e.timeStamp, e.clientY]] };
  });
  el.addEventListener("pointermove", (e) => {
    if (!drag || e.pointerId !== drag.id) return;
    const dy = e.clientY - drag.y0;
    if (!drag.moved) {
      if (Math.abs(dy) < 6) return; // 그냥 누른 것(버튼 누르기)과 끌기를 가른다
      drag.moved = true;
      el.setPointerCapture(e.pointerId);
      el.classList.remove("settling");
      el.classList.add("dragging");
      getSelection()?.removeAllRanges();
    }
    drag.pts.push([e.timeStamp, e.clientY]);
    if (drag.pts.length > 6) drag.pts.shift();
    const full = fullHeight();
    const want = drag.h0 - dy;
    if (want > full) paint(full + rubber(want - full));
    else if (want < 0) paint(0, rubber(-want));
    else paint(want);
  });
  function release(e) {
    if (!drag || e.pointerId !== drag.id) return;
    const d = drag;
    drag = null;
    if (!d.moved) return;
    draggedAt = performance.now();
    el.classList.remove("dragging");
    // 마지막 ~100ms 의 빠르기(px/ms, 위가 -)
    const last = d.pts[d.pts.length - 1];
    const from = d.pts.find((p) => last[0] - p[0] <= 100) ?? d.pts[0];
    const v = last[0] > from[0] ? (last[1] - from[1]) / (last[0] - from[0]) : 0;
    if (v < -0.3) settle(true);
    else if (v > 0.3) settle(false);
    else settle(moreEl.offsetHeight > fullHeight() / 2);
  }
  el.addEventListener("pointerup", release);
  el.addEventListener("pointercancel", release);

  return {
    el,
    moreEl,
    /** 펼칠 게 있는지(경로 안내를 열 수 있는지). 없어지면 펼친 시트도 접는다 */
    setExpandable(on) {
      if (on === expandable) return;
      expandable = on;
      handle.tabIndex = on ? 0 : -1;
      handle.setAttribute("aria-hidden", String(!on));
      if (!on && open) settle(false);
    },
    toggle: (toOpen = !open) => settle(toOpen),
    get open() {
      return open;
    },
  };
}

/**
 * 지도가 화면을 채우고 위엔 카드, 아래엔 끌 수 있는 시트가 뜨는 틀(P-03 · P-04 · P-04a · P-05).
 * covered() 가 지도 맞춤 여백을 돌려준다 — 위 카드(topCover)와 아래 시트가 가린 만큼.
 * 아래는 접힌 시트 높이 기준 — 펼치고 접을 때마다 지도가 튀지 않게.
 */
function mapLayout({ top, sheet, topCover = () => top }) {
  const mapBox = h("div", { class: "map full" });
  const bottom = bottomSheet(sheet);
  const area = h("div", { class: "map-area" }, mapBox, top, bottom.el);
  // 저작권 표시(ⓘ)가 시트 밑에 숨지 않게 시트 높이만큼 올린다(펼치면 같이 올라간다)
  const sync = () => area.style.setProperty("--sheet-h", `${bottom.el.offsetHeight}px`);
  const resize = new ResizeObserver(sync);
  resize.observe(bottom.el);
  function covered() {
    const box = area.getBoundingClientRect();
    const cover = topCover();
    const topEdge = cover && !cover.hidden ? cover.getBoundingClientRect().bottom - box.top : 0;
    const folded = bottom.el.offsetHeight - bottom.moreEl.offsetHeight;
    // 위는 카드 아래로 픽업 핀 + '픽업 위치' 이름표(끝점에서 위로 ~83) 높이만큼 92,
    // 아래는 점 반지름(12)과 여유 24, 양옆은 가운데 정렬 이름표 반 폭(~40) 44 — 이름표는 모두 표식 위에 붙는다
    return { top: topEdge + 92, bottom: folded + 24, left: 44, right: 44 };
  }
  return { area, mapBox, sheet: bottom, covered, destroy: () => resize.disconnect() };
}

// ── 화면

const screens = {
  loading() {
    const el = h("section", { class: "screen" }, brandLogo(), h("p", { class: "m-regular muted" }, failure ?? "불러오는 중…"));
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
    const map = createMap(mapBox, { carLabel: `${view.driverCallName} 님 차` });

    const el = h(
      "section",
      { class: "screen soft" },
      brandLogo(),
      h(
        "div",
        { class: "stack", style: "gap:20px" },
        h("div", { class: "stack", style: "gap:16px" }, h("div", { class: "stack", style: "gap:4px" }, title, sub), h("div", { class: "eta-box" }, h("span", { class: "label" }, "도착 예정 시간"), eta)),
        h("div", { class: "stack", style: "gap:16px" }, vehicle.el, h("div", { class: "card map-card" }, mapBox, place.el)),
      ),
      h("div", { class: "grow" }),
      h("div", { class: "stack actions", style: "gap:12px" }, h("p", { class: "caption" }, "앱 설치, 로그인 없이 이 페이지에서 끝나요"), next),
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
      h("div", { class: "stack actions", style: "gap:12px" }, allow, decline),
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

  // P-03 대기 — 지도 위에 도착 예정 카드, 바뀌었으면 그 아래 검은 알림(5초 보이고 사라짐). 아래 시트에 차량 · 픽업 위치(펼치면 '경로 안내')
  wait() {
    const BANNER_MS = 5000;
    const banner = h("div", { class: "banner-changed", role: "status", "aria-live": "polite" }, icon("info-white-20", 20), h("span", {}, "도착 시간이 변경되었어요"));
    let bannerTimer = null;
    function flashBanner() {
      banner.classList.add("on");
      clearTimeout(bannerTimer);
      bannerTimer = setTimeout(() => banner.classList.remove("on"), BANNER_MS);
    }
    const who = h("p", { class: "who" });
    const card = etaCard({ head: who, art: "p03" });
    const rows = carRows();
    const top = h("div", { class: "float-top" }, card.el, banner);
    // 알림은 잠깐 떴다 사라지니 지도 맞춤은 도착 예정 카드까지만 피한다(사라질 때 지도가 튀지 않게)
    const layout = mapLayout({ top, sheet: { body: [rows.el, h("p", { class: "note" }, "도착 5분 전에 픽업 안내 문자를 보내드려요.")], more: [routeButton()] }, topCover: () => card.el });
    let map = null;

    const el = h("section", { class: "screen map-screen" }, layout.area);

    return {
      el,
      update(v) {
        map ??= createMap(layout.mapBox, { carLabel: `${v.driverCallName} 님 차`, padding: layout.covered, pickupLabel: true });
        // 새 변경이 오면 5초 보이고 사라진다(같은 변경은 한 번만)
        const change = v.etaChange ? `${v.etaChange.from}-${v.etaChange.to}` : null;
        if (change && change !== seenEtaChange) flashBanner();
        seenEtaChange = change ?? seenEtaChange;
        set(who, `${v.driverCallName} 님의 도착 예정`);
        set(card.time, hhmm(v.arrivalAt));
        const sec = secondsLeft(v);
        set(card.left, sec < 60 ? "곧 도착해요" : `${minutesText(sec)} 남았어요`);
        rows.update(v);
        layout.sheet.setExpandable(canRoute(v));
        map.update(mapView(v));
      },
      destroy() {
        clearTimeout(bannerTimer);
        layout.destroy();
        map?.destroy();
      },
    };
  },

  // P-04 곧 도착해요 — 지도 위에 '곧 도착해요' 카드, 아래 시트에 차량 · 픽업 위치(펼치면 '경로 안내')
  soon() {
    const card = etaCard({ head: h("div", { class: "eta-head" }, icon("dot", 20), h("span", {}, "곧 도착해요")), accent: true, art: "p04" });
    const rows = carRows();
    rows.el.classList.add("inset");
    const top = h("div", { class: "float-top" }, card.el);
    const layout = mapLayout({ top, sheet: { body: [rows.el], more: [routeButton()] } });
    let map = null;

    const el = h("section", { class: "screen map-screen" }, layout.area);

    return {
      el,
      update(v) {
        map ??= createMap(layout.mapBox, { carLabel: `${v.driverCallName} 님 차`, padding: layout.covered, pickupLabel: true });
        set(card.time, hhmm(v.arrivalAt));
        const sec = secondsLeft(v);
        set(card.left, sec < 60 ? "곧 픽업 위치에 도착해요" : `${minutesText(sec)} 뒤 픽업 위치에 도착해요`);
        rows.update(v);
        layout.sheet.setExpandable(canRoute(v));
        map.update(mapView(v));
      },
      destroy() {
        layout.destroy();
        map?.destroy();
      },
    };
  },

  // P-04a 경로 안내 — 내 위치에서 픽업 위치까지 가장 빠른 걷는 길(서버 계산). '<'·뒤로 가기로 돌아간다.
  // '이 경로를 따라 이동해 주세요' 안내는 처음 5초만 보이고 사라진다 — 걷는 길 카드의 ⓘ 를 누르면 다시 5초.
  route() {
    const GUIDE_MS = 5000;
    const back = h("button", { class: "back", type: "button", "aria-label": "뒤로", onclick: closeRoute }, icon("back", 32));
    const walkLabel = h("p", { class: "s-regular muted" }, "현재 위치에서 가는 가장 빠른 길이에요");
    const walkMain = h("p", { class: "walk-main" });
    const pending = h("p", { class: "walk-pending" }, "경로를 찾고 있어요");
    const lines = h("p", { class: "s-regular guide-lines" });
    const guide = h("div", { class: "guide-card", role: "status", "aria-live": "polite" }, icon("info-white", 24), h("div", { class: "stack grow-x", style: "gap:4px" }, h("p", { class: "m-semibold guide-title" }, "이 경로를 따라 이동해 주세요"), lines));
    let guideTimer = null;
    function flashGuide() {
      guide.classList.add("on");
      clearTimeout(guideTimer);
      guideTimer = setTimeout(() => guide.classList.remove("on"), GUIDE_MS);
    }
    const info = h("button", { class: "info-btn", type: "button", "aria-label": "경로 안내 다시 보기", onclick: flashGuide }, icon("info", 24));
    const walkCard = h("div", { class: "walk-card glass accent" }, h("div", { class: "walk-tile" }, icon("footsteps", 24)), h("div", { class: "stack grow-x" }, walkLabel, walkMain, pending), info);
    const rows = carRows();
    rows.el.classList.add("inset");
    const top = h("div", { class: "float-top under-bar" }, walkCard, guide);
    // 안내는 잠깐 떴다 사라지니 지도 맞춤은 걷는 길 카드까지만 피한다
    const layout = mapLayout({ top, sheet: { body: [rows.el] }, topCover: () => walkCard }); // 이미 경로 안내 화면 — 펼칠 것 없음
    let map = null;
    let guided = false; // 들어와서 첫 안내를 보였는지(길을 아직 찾는 중이면 길이 나올 때)

    const el = h("section", { class: "screen map-screen" }, h("div", { class: "top-bar" }, back, h("p", { class: "title-m" }, "경로 안내")), layout.area);

    return {
      el,
      update(v) {
        map ??= createMap(layout.mapBox, { carLabel: `${v.driverCallName} 님 차`, padding: layout.covered, pickupLabel: true });
        rows.update(v);
        const w = v.walk;
        show(walkLabel, !!w);
        show(walkMain, !!w);
        show(pending, !w);
        if (w) set(walkMain, walkText(w));
        setLines(lines, followLines(v.driverCallName));
        if (w && !guided) {
          guided = true;
          flashGuide();
        }
        map.update(mapView(v, { route: true, car: false }));
      },
      destroy() {
        clearTimeout(guideTimer);
        layout.destroy();
        map?.destroy();
      },
    };
  },

  // P-05 정차 · 대기 타이머 — 지도 위에 타이머 카드(차 화면과 같은 1분, 서버 시계 기준), 아래 시트에 차량 · 픽업 위치(펼치면 '경로 안내').
  // 카드 색은 남은 시간이 줄수록 파랑 → 빨강(timer-color.js). 내 위치 → 픽업 위치 걷는 길은 지도에 주황 선으로.
  timer() {
    const NS = "http://www.w3.org/2000/svg";
    const R = 41.625; // 지름 90 · 두께 6.75(피그마 고리)
    const C = 2 * Math.PI * R;
    const svg = document.createElementNS(NS, "svg");
    svg.setAttribute("viewBox", "0 0 90 90");
    const track = document.createElementNS(NS, "circle");
    const arc = document.createElementNS(NS, "circle");
    for (const [cls, node] of [["track", track], ["arc", arc]]) {
      node.setAttribute("cx", "45");
      node.setAttribute("cy", "45");
      node.setAttribute("r", String(R));
      node.setAttribute("class", cls);
      svg.append(node);
    }
    arc.setAttribute("stroke-dasharray", String(C));
    const sec = h("p", { class: "sec" });
    const sub = h("p", { class: "timer-sub" });
    const card = h(
      "div",
      { class: "timer-card" },
      h("div", { class: "ring", role: "timer", "aria-live": "off" }, svg, sec),
      h("div", { class: "stack grow-x", style: "gap:2px" }, h("p", { class: "timer-title" }, "차량이 픽업 위치에", h("br"), "도착했어요"), sub),
    );
    const rows = carRows();
    rows.el.classList.add("inset");
    const layout = mapLayout({ top: h("div", { class: "float-top" }, card), sheet: { body: [rows.el], more: [routeButton()] } });
    let map = null;

    const el = h("section", { class: "screen map-screen" }, layout.area);

    let tick = null;
    let toneAt = null; // 마지막으로 색을 칠한 초 — 1초에 한 번만 바꾸고 사이는 CSS 가 1초 동안 섞는다
    function draw() {
      const t = view?.timer;
      if (!t) return;
      const remaining = Math.max(0, t.endsAt - serverNow());
      const s = Math.ceil(remaining / 1000);
      set(sec, `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`);
      arc.setAttribute("stroke-dashoffset", String(C * (1 - remaining / (t.waitSeconds * 1000))));
      arc.style.visibility = remaining === 0 ? "hidden" : "";
      set(sub, remaining === 0 ? "대기 시간이 지났어요" : `${waitText(t.waitSeconds)} 안에 탑승해 주세요`);
      if (s !== toneAt) {
        toneAt = s;
        const tone = timerTone(s);
        card.style.backgroundColor = tone.card;
        track.style.stroke = tone.track;
        sub.style.color = tone.sub;
      }
    }

    return {
      el,
      update(v) {
        map ??= createMap(layout.mapBox, { carLabel: `${v.driverCallName} 님 차`, padding: layout.covered, pickupLabel: true });
        rows.update(v);
        layout.sheet.setExpandable(canRoute(v));
        map.update(mapView(v, { route: true }));
        draw();
        tick ??= setInterval(draw, 200);
      },
      destroy() {
        clearInterval(tick);
        layout.destroy();
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
  // 오는 중에 연 P-04a 는 차가 도착하면 닫는다(1분 타이머가 먼저), P-05 에서 연 것은 그대로
  if (routeOpen && canRoute(view) && (view.phase !== "ARRIVED" || routeFrom === "ARRIVED")) return "route";
  if (view.phase === "ARRIVED") return "timer";
  return view.phase === "ARRIVING_SOON" ? "soon" : "wait";
}

function render() {
  const key = screenKey();
  // P-04a 를 연 채 정차·종료·길 없음으로 화면이 바뀌면 쌓아 둔 기록도 걷는다 — 안 그러면 '뒤로'가 한 번 헛돈다
  if (current?.key === "route" && key !== "route" && history.state?.wayRoute) {
    routeOpen = false;
    routeFrom = null;
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
