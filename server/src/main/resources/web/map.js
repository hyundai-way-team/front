// 동승자 웹 지도 — 앱과 같은 오프라인 지도(way.pmtiles)를 서버에서 받아 그린다. 외부 지도 API 없음.
import { AttributionControl, LngLatBounds, Map, Marker, addProtocol } from "/web/vendor/maplibre-gl.mjs";

let protocolReady = false;
let stylePromise = null;

function style() {
  if (!protocolReady) {
    // eslint-disable-next-line no-undef -- pmtiles.js(UMD)가 전역 pmtiles 를 만든다
    const protocol = new pmtiles.Protocol();
    addProtocol("pmtiles", protocol.tile);
    protocolReady = true;
  }
  // 앱용 style.json 의 asset:// 주소를 이 서버 주소로 바꾼다
  stylePromise ??= fetch("/map/style.json")
    .then((r) => {
      if (!r.ok) throw new Error(`지도 스타일 ${r.status}`);
      return r.text();
    })
    .then((text) => JSON.parse(text.replaceAll("asset://map/", `${location.origin}/map/`)));
  return stylePromise;
}

// 지도 표식 — 피그마 Component 페이지 확정본: Icons/Marker(픽업 핀) · Location Dot(현재 위치 · 차량 위치) · Tag/Location(이름표, Mobile Web).
// 이름표는 핀·점 바로 위 가운데. 그림 둘레의 빛·그림자 여백(핀 10 · 점 16)은 CSS 음수 여백으로 빼서
// 핀은 끝점이, 점은 가운데가 좌표에 오게 한다(MARK 의 anchor · offset).
const MARK = {
  pin: { src: "/web/icons/marker-pickup.svg", anchor: "bottom", offset: [0, 0] },
  car: { src: "/web/icons/dot-car.svg", anchor: "bottom", offset: [0, 12] }, // 점 지름 24 의 절반만큼 내려 점 가운데를 좌표에
  me: { src: "/web/icons/dot-me.svg", anchor: "bottom", offset: [0, 12] },
};

function el(kind, label) {
  const wrap = document.createElement("div");
  wrap.className = `mk-wrap ${kind}`;
  let labelEl = null;
  if (label !== undefined) {
    labelEl = document.createElement("div");
    labelEl.className = `mk-tag ${kind}`;
    labelEl.textContent = label;
    wrap.append(labelEl);
  }
  const img = document.createElement("img");
  img.className = kind === "pin" ? "mk-pin" : "mk-dot";
  img.src = MARK[kind].src;
  img.alt = "";
  wrap.append(img);
  return { wrap, labelEl };
}

const toLngLat = (p) => [p.lng, p.lat];

// 카드 속 지도(P-01)에서 점들을 맞출 때 가장자리 여백 — 위는 점 위 이름표(점 가운데에서 ~48) · 핀(45) 자리,
// 아래는 점 반지름(12)과 여유, 양옆은 가운데 정렬 이름표 반 폭(~40)
const CARD_PADDING = { top: 56, bottom: 24, left: 44, right: 44 };

/** 걷는 길 색 — 동승자 주황(피그마 v1 걷는 길 · '나' 점 #d9822b = app.css --passenger) */
function walkColor() {
  return getComputedStyle(document.documentElement).getPropertyValue("--passenger").trim() || "#d9822b";
}

/** 차 길 색 — 파랑(app.css --primary-500, 차 화면의 경로 선과 같은 색) */
function carRouteColor() {
  return getComputedStyle(document.documentElement).getPropertyValue("--primary-500").trim() || "#3b67f6";
}

/**
 * 지도 하나. update({ pickup, car, me, meLabel, route, carRoute }) 로 점·걷는 길·차 길을 옮긴다.
 * route 는 서버가 계산한 보행 경로 [{lat,lng}…](주황), carRoute 는 차가 오는 길(파랑, 차가 지나온 부분은 서버가 잘라 보냄)
 * — 없으면 선을 그리지 않는다(가짜 직선 금지).
 * 손으로 끌고·두 손가락/휠로 확대할 수 있다(회전·기울이기는 끔). 처음엔 점들과 길이 다 보이게 자동으로 맞추고,
 * 사용자가 지도를 한 번 만지면 그 화면에선 자동 맞춤을 멈춘다(점은 계속 움직인다). 화면이 바뀌면 지도를 새로 만드니 다시 자동 맞춤.
 * padding: 지도 위에 카드가 떠 있으면 가려지는 만큼 — 객체나, 맞출 때마다 부르는 함수({top,bottom,left,right}).
 * pickupLabel: 픽업 핀 위에 '픽업 위치' 이름표(전체 화면 지도 P-03 · P-04 · P-04a · P-05 — 피그마 P-03).
 */
export function createMap(container, { carLabel = "차", padding = CARD_PADDING, pickupLabel = false } = {}) {
  let map = null;
  let latest = null;
  const markers = {};
  let carAnim = null;
  let lastFitKey = "";
  let userMoved = false; // 사용자가 지도를 움직였으면 자동 맞춤을 멈춘다
  let destroyed = false;

  const empty = document.createElement("div");
  empty.className = "map-empty";
  empty.textContent = "지도를 불러오는 중…";
  container.append(empty);

  style()
    .then((s) => {
      if (destroyed) return; // 스타일을 받기 전에 화면이 바뀌었다
      map = new Map({
        container,
        style: s,
        center: latest ? toLngLat(latest.pickup) : [126.942, 37.5567],
        zoom: 16,
        // 끌기 · 두 손가락 확대 · 휠 확대 · 두 번 눌러 확대. 회전·기울이기는 끈다(북쪽이 늘 위)
        dragRotate: false,
        pitchWithRotate: false,
        touchPitch: false,
        maxPitch: 0,
        attributionControl: false,
        fadeDuration: 0,
      });
      map.touchZoomRotate.disableRotation();
      map.keyboard.disableRotation();
      map.addControl(new AttributionControl({ compact: true }), "bottom-right");
      // 사용자가 만든 움직임(originalEvent 가 있음)만 — fitBounds 같은 자동 이동은 거른다
      map.on("movestart", (e) => {
        if (e.originalEvent) userMoved = true;
      });
      map.on("load", () => {
        empty.remove();
        // 좁은 지도에선 저작권 표시가 펼쳐진 채 시작해 지도를 가린다 — 'ⓘ' 로 접어 둔다(누르면 펼쳐진다)
        container.querySelector(".maplibregl-ctrl-attrib")?.classList.remove("maplibregl-compact-show");
        const round = { "line-cap": "round", "line-join": "round" };
        // 차가 오는 길 — 흰 테두리 위에 파란 선(걷는 길·점보다 아래)
        map.addSource("car-route", { type: "geojson", data: line([]) });
        map.addLayer({ id: "car-route-casing", type: "line", source: "car-route", layout: round, paint: { "line-color": "#ffffff", "line-width": 9 } });
        map.addLayer({ id: "car-route", type: "line", source: "car-route", layout: round, paint: { "line-color": carRouteColor(), "line-width": 5 } });
        // 걷는 길 — 흰 테두리 위에 동승자 주황 선(모든 화면 같은 색)
        map.addSource("walk", { type: "geojson", data: line([]) });
        map.addLayer({ id: "walk-casing", type: "line", source: "walk", layout: round, paint: { "line-color": "#ffffff", "line-width": 9 } });
        map.addLayer({ id: "walk", type: "line", source: "walk", layout: round, paint: { "line-color": walkColor(), "line-width": 5 } });
        if (latest) apply(latest, true);
      });
    })
    .catch(() => {
      empty.textContent = "지도를 불러오지 못했어요";
    });

  function line(coords) {
    return { type: "Feature", geometry: { type: "LineString", coordinates: coords }, properties: {} };
  }

  function marker(key, kind, label, at) {
    if (!at) {
      markers[key]?.marker.remove();
      delete markers[key];
      return;
    }
    let m = markers[key];
    if (!m) {
      const { wrap, labelEl } = el(kind, label);
      const { anchor, offset } = MARK[kind];
      m = { marker: new Marker({ element: wrap, anchor, offset }).setLngLat(toLngLat(at)).addTo(map), labelEl };
      markers[key] = m;
    } else if (key !== "car") {
      m.marker.setLngLat(toLngLat(at));
    }
    if (m.labelEl && label !== undefined && m.labelEl.textContent !== label) m.labelEl.textContent = label;
    return m;
  }

  // 차는 5초마다 보고가 와서 한 번에 뛰지 않게 부드럽게 옮긴다
  function moveCar(m, to) {
    const from = m.marker.getLngLat();
    const target = toLngLat(to);
    if (carAnim) cancelAnimationFrame(carAnim);
    const start = performance.now();
    const duration = 1200;
    const step = (t) => {
      const k = Math.min(1, (t - start) / duration);
      m.marker.setLngLat([from.lng + (target[0] - from.lng) * k, from.lat + (target[1] - from.lat) * k]);
      if (k < 1) carAnim = requestAnimationFrame(step);
    };
    carAnim = requestAnimationFrame(step);
  }

  function apply(v, first) {
    marker("pickup", "pin", pickupLabel ? "픽업 위치" : undefined, v.pickup);
    const hadCar = !!markers.car;
    const car = marker("car", "car", carLabel, v.car);
    if (car && hadCar) moveCar(car, v.car);
    marker("me", "me", v.meLabel ?? "현재 위치", v.me);
    map.getSource("walk")?.setData(line((v.route ?? []).map(toLngLat)));
    map.getSource("car-route")?.setData(line((v.carRoute ?? []).map(toLngLat)));
    fit(v, first);
  }

  /** 지금 가려진 가장자리(카드·시트) — 지도 높이보다 크면 줄여서 맞출 자리를 남긴다 */
  function edges() {
    const p = { ...(typeof padding === "function" ? padding() : padding) };
    const room = container.clientHeight - 60;
    if (room > 0 && p.top + p.bottom > room) {
      const k = room / (p.top + p.bottom);
      p.top *= k;
      p.bottom *= k;
    }
    for (const side of ["top", "bottom", "left", "right"]) p[side] = Math.max(0, Math.round(p[side]));
    return p;
  }

  function fit(v, first) {
    if (userMoved) return; // 사용자가 옮긴 화면을 덮어쓰지 않는다
    const route = v.route ?? [];
    const carRoute = v.carRoute ?? [];
    const points = [v.pickup, v.car, v.me, ...route, ...carRoute].filter(Boolean);
    const pad = edges();
    // 점이 크게 움직였거나 가려진 자리가 바뀌었을 때만 다시 맞춘다(작은 흔들림에 화면이 계속 움직이지 않게)
    const key = [v.pickup, v.car, v.me, route[0], route[route.length - 1]]
      .map((p) => (p ? `${p.lat.toFixed(4)},${p.lng.toFixed(4)}` : "-"))
      .join("|") + `|${route.length}|${carRoute.length > 0}|${pad.top},${pad.bottom},${pad.left},${pad.right}`;
    if (!first && key === lastFitKey) return;
    lastFitKey = key;
    if (points.length === 1) {
      // 점 하나는 가운데로 — 카드·시트가 떠 있는 지도면 가려지지 않은 자리 가운데로
      // (camera padding 은 지도에 남아 다음 맞춤에 더해지니 offset 으로)
      const offset = typeof padding === "function" ? [(pad.left - pad.right) / 2, (pad.top - pad.bottom) / 2] : [0, 0];
      map.easeTo({ center: toLngLat(v.pickup), zoom: 16.5, offset, duration: 0 });
      return;
    }
    const bounds = new LngLatBounds();
    points.forEach((p) => bounds.extend(toLngLat(p)));
    map.fitBounds(bounds, { padding: pad, maxZoom: 17.5, duration: first ? 0 : 600 });
  }

  return {
    update(v) {
      latest = v;
      if (map && map.loaded()) apply(v, false);
    },
    destroy() {
      destroyed = true;
      if (carAnim) cancelAnimationFrame(carAnim);
      map?.remove();
    },
  };
}

/** 두 좌표 사이 거리(m). */
export function distanceMeters(a, b) {
  const R = 6371008.8;
  const rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad;
  const dLng = (b.lng - a.lng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

/** 북쪽·동쪽으로 m 만큼 옮긴 좌표. */
export function offsetMeters(p, north, east) {
  const R = 6371008.8;
  const rad = Math.PI / 180;
  return { lat: p.lat + north / R / rad, lng: p.lng + east / (R * Math.cos(p.lat * rad)) / rad };
}
