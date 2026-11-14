// P-05 대기 타이머 카드 색 — 남은 시간이 줄수록 파랑 → 빨강. 화면 코드 없이 숫자만 다루는 작은 함수들.
// 남은 40초 이상은 맨 앞 색, 15초 이하는 맨 끝 색, 그 사이 25초는 멈춤점을 같은 간격으로 두고 RGB 로 섞는다.
// 15초는 차 화면에 '전화 걸까요'가 뜨는 때와 같다. 피그마 P-05 0:42(파랑) · 0:15(빨강)와 끝 색이 맞는다.

/** 카드 배경 — 사용자가 정한 9색(위에서 아래 순서) */
export const CARD_STOPS = ["#3B67F6", "#5263E0", "#685ECA", "#7E5AB3", "#95569D", "#AC5187", "#C24D70", "#D8485A", "#EF4444"];
/** 타이머 원의 바탕 고리 — 피그마 파랑 카드 primary-400 · 빨강 카드 error-400 */
export const TRACK_STOPS = ["#6084FA", "#F87171"];
/** '1분 안에 탑승해 주세요' 글자 — 피그마 파랑 카드 primary-100 · 빨강 카드 error-100 */
export const SUB_STOPS = ["#DBE3FE", "#FEE2E2"];

export const CALM_SECONDS = 40;
export const URGENT_SECONDS = 15;

/** 남은 초 → 0(여유, 40초 이상) ~ 1(촉박, 15초 이하) */
export function urgency(seconds) {
  const t = (CALM_SECONDS - seconds) / (CALM_SECONDS - URGENT_SECONDS);
  return Math.min(1, Math.max(0, t));
}

function rgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function hex([r, g, b]) {
  return `#${[r, g, b].map((c) => Math.round(c).toString(16).padStart(2, "0")).join("").toUpperCase()}`;
}

/** 같은 간격 멈춤점(stops) 위 t(0~1) 자리의 색 — 이웃한 두 멈춤점을 RGB 로 섞는다 */
export function gradientAt(stops, t) {
  const at = Math.min(1, Math.max(0, t)) * (stops.length - 1);
  const i = Math.min(stops.length - 2, Math.floor(at));
  const k = at - i;
  const a = rgb(stops[i]);
  const b = rgb(stops[i + 1]);
  return hex(a.map((c, j) => c + (b[j] - c) * k));
}

/** 남은 초 → 카드 배경 hex */
export function timerColor(seconds) {
  return gradientAt(CARD_STOPS, urgency(seconds));
}

/** 남은 초 → 카드 · 바탕 고리 · 작은 글자 색(같은 비율로 함께 바뀐다) */
export function timerTone(seconds) {
  const t = urgency(seconds);
  return { card: gradientAt(CARD_STOPS, t), track: gradientAt(TRACK_STOPS, t), sub: gradientAt(SUB_STOPS, t) };
}
