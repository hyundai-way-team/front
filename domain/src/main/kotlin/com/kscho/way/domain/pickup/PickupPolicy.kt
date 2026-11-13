package com.kscho.way.domain.pickup

/** 기획서 수치. '미정'인 값은 우선 정해 두고 표시해 둔다. */
object PickupPolicy {
  /** 정차 후 대기 1분 — 불법 주정차 단속의 예외 시간(기획 3-9). */
  const val WAIT_SECONDS = 60

  /** 대기 15초 남았는데 미탑승이면 전화 연결 제안(기획 3-9). */
  const val CALL_OFFER_REMAINING_SECONDS = 15

  /** 도착 5분 전 "곧 도착해요"(기획 3-6). */
  const val ARRIVING_SOON_SECONDS = 5 * 60

  /** ETA 재발송 기준 — 기획서 '미정'. 우선 3분 이상 바뀔 때 다시 보낸다. */
  const val ETA_RESEND_THRESHOLD_SECONDS = 3 * 60

  /** 차량 위치·ETA 를 서버에 보내는 간격. */
  const val DRIVER_REPORT_INTERVAL_MILLIS = 5_000L

  /** D-10: 목적지를 정해 둔 픽업은 탑승 뒤 이만큼 지나면 목적지 안내를 Pleos 내비에 넘긴다. */
  const val DESTINATION_HANDOVER_SECONDS = 10
}
