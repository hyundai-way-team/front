package com.kscho.way.domain.model

enum class LocationSharing {
  /** 아직 링크를 안 열었거나 허용 여부를 고르지 않음 */
  NOT_YET,
  SHARING,
  /** 거부 — 운전자 화면에 "위치 미공유" */
  DECLINED,
  /** 탑승 완료로 공유 끝 */
  ENDED,
}

/** 동승자 웹 링크 쪽 상태(서버가 알려 준다). */
data class PassengerStatus(
  val linkOpened: Boolean = false,
  val sharing: LocationSharing = LocationSharing.NOT_YET,
  val location: LatLng? = null,
  /** 동승자 웹 '늦어요'를 눌렀다(와이어프레임 v2 P-03) */
  val late: Boolean = false,
  /** 동승자 위치 → 픽업 위치 걷는 길(서버가 지도 도로선으로 찾음) — 동승자 웹 P-05 와 같은 경로. 아직 없으면 null */
  val walk: WalkRoute? = null,
)

/** 걷는 길 — 지도에 그릴 점들과 거리·걸리는 시간. */
data class WalkRoute(val path: List<LatLng>, val meters: Int, val seconds: Int)
