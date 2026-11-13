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

function el(className, label) {
  const wrap = document.createElement("div");
  wrap.className = `mk-wrap ${className}`;
  const dot = document.createElement("div");
  dot.className = className === "pin" ? "mk-pin" : `mk-dot ${className}`;
  wrap.append(dot);
  let labelEl = null;
  if (label !== undefined) {
    labelEl = document.createElement("div");
    labelEl.className = "mk-label";
    labelEl.textContent = label;
    wrap.append(labelEl);
  }
  return { wrap, labelEl };
}

const toLngLat = (p) => [p.lng, p.lat];

/**
 * 작은 지도 하나. update({ pickup, car, me, meLabel, route }) 로 점·걷는 길을 옮긴다.
 * route 는 서버가 계산한 보행 경로 [{lat,lng}…] — 없으면 선을 그리지 않는다(가짜 직선 금지).
 * 손으로 움직이지 않게(카드 속 지도) 하고, 점들과 길이 다 보이게 자동으로 맞춘다.
 */
export function createMap(container, { carLabel = "차" } = {}) {
  let map = null;
  let latest = null;
  const markers = {};
  let carAnim = null;
  let lastFitKey = "";

  const empty = document.createElement("div");
  empty.className = "map-empty";
  empty.textContent = "지도를 불러오는 중…";
  container.append(empty);

  style()
    .then((s) => {
      map = new Map({
        container,
        style: s,
        center: latest ? toLngLat(latest.pickup) : [126.942, 37.5567],
        zoom: 16,
        interactive: false,
        attributionControl: false,
        fadeDuration: 0,
      });
      map.addControl(new AttributionControl({ compact: true }), "bottom-right");
      map.on("load", () => {
        empty.remove();
        // 걷는 길(P-04a·P-05) — 흰 테두리 위에 파란 선
        map.addSource("walk", { type: "geojson", data: line([]) });
        const round = { "line-cap": "round", "line-join": "round" };
        map.addLayer({ id: "walk-casing", type: "line", source: "walk", layout: round, paint: { "line-color": "#ffffff", "line-width": 9 } });
        map.addLayer({ id: "walk", type: "line", source: "walk", layout: round, paint: { "line-color": "#3b67f6", "line-width": 5 } });
        if (latest) apply(latest, true);
      });
    })
    .catch(() => {
      empty.textContent = "지도를 불러오지 못했어요";
    });

  function line(coords) {
    return { type: "Feature", geometry: { type: "LineString", coordinates: coords }, properties: {} };
  }

  function marker(key, className, label, at, anchor) {
    if (!at) {
      markers[key]?.marker.remove();
      delete markers[key];
      return;
    }
    let m = markers[key];
    if (!m) {
      const { wrap, labelEl } = el(className, label);
      m = { marker: new Marker({ element: wrap, anchor }).setLngLat(toLngLat(at)).addTo(map), labelEl };
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
    marker("pickup", "pin", undefined, v.pickup, "bottom");
    const hadCar = !!markers.car;
    const car = marker("car", "car", carLabel, v.car, "center");
    if (car && hadCar) moveCar(car, v.car);
    marker("me", "me", v.meLabel ?? "현재 위치", v.me, "center");
    map.getSource("walk")?.setData(line((v.route ?? []).map(toLngLat)));
    fit(v, first);
  }

  function fit(v, first) {
    const route = v.route ?? [];
    const points = [v.pickup, v.car, v.me, ...route].filter(Boolean);
    // 점이 크게 움직였을 때만 다시 맞춘다(작은 흔들림에 화면이 계속 움직이지 않게)
    const key = [v.pickup, v.car, v.me, route[0], route[route.length - 1]]
      .map((p) => (p ? `${p.lat.toFixed(4)},${p.lng.toFixed(4)}` : "-"))
      .join("|") + `|${route.length}`;
    if (!first && key === lastFitKey) return;
    lastFitKey = key;
    if (points.length === 1) {
      map.jumpTo({ center: toLngLat(v.pickup), zoom: 16.5 });
      return;
    }
    const bounds = new LngLatBounds();
    points.forEach((p) => bounds.extend(toLngLat(p)));
    map.fitBounds(bounds, { padding: { top: 36, bottom: 70, left: 40, right: 70 }, maxZoom: 17.5, duration: first ? 0 : 600 });
  }

  return {
    update(v) {
      latest = v;
      if (map && map.loaded()) apply(v, false);
    },
    destroy() {
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
