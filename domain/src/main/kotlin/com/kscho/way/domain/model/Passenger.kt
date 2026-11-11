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
)
