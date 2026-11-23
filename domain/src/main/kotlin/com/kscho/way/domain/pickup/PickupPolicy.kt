package com.kscho.way.domain.pickup

object PickupPolicy {
  const val WAIT_SECONDS = 60

  const val CALL_OFFER_REMAINING_SECONDS = 15

  const val ARRIVING_SOON_SECONDS = 5 * 60

  const val ETA_RESEND_THRESHOLD_SECONDS = 3 * 60

  const val DRIVER_REPORT_INTERVAL_MILLIS = 5_000L

  const val CONNECT_AFTER_BOARDING_SECONDS = 5
}
