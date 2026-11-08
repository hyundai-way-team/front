package com.kscho.way.pickup

/** MVP 3-9 대기 타이머: 픽업 위치에 정차하면 1분, 15초 남았는데 미탑승이면 전화 연결을 음성으로 제안. */
object PickupWaitTimer {
  const val WAIT_MILLIS = 60_000L
  const val CALL_OFFER_REMAINING_MILLIS = 15_000L

  /** 전화 연결을 제안할 시각([PickupSession.arrivedAtMillis]와 같은 시계). 제안할 일이 없으면 null. */
  fun callOfferAt(session: PickupSession): Long? {
    val arrived = session.arrivedAtMillis ?: return null
    if (session.boarded || session.callOffered) return null
    return arrived + WAIT_MILLIS - CALL_OFFER_REMAINING_MILLIS
  }
}
