package com.kscho.way.gleo

import com.kscho.way.pickup.PickupSession
import kotlin.math.ceil

/** 픽업 상태 → Gleo 에 돌려줄 답. 순수 함수라 단위 테스트로 문장을 고정한다. */
object PickupGleoReplies {

  /** "픽업 상황 알려줘" */
  fun status(session: PickupSession?): GleoReply {
    val fn = GleoActions.GET_PICKUP_STATUS
    if (session == null) return GleoReply(fn, GleoStatus.SUCCESS, "There is no pickup in progress.")
    if (session.boarded) return GleoReply(fn, GleoStatus.SUCCESS, "${session.passengerName} has already boarded.", data = listOf(row(session)))

    val name = session.passengerName
    val arrival =
      when (val minutes = minutesToArrival(session)) {
        null -> "Arrival time is not available yet."
        0 -> "Arriving now."
        1 -> "Arriving in about 1 minute."
        else -> "Arriving in about $minutes minutes."
      }
    val passenger =
      when {
        !session.passengerCheckedLink -> "$name has not opened the pickup link yet."
        session.passengerSharingLocation -> "$name has checked the pickup location and is sharing location."
        else -> "$name has checked the pickup location but is not sharing location."
      }
    return GleoReply(fn, GleoStatus.SUCCESS, "Picking up $name at ${session.pickupPlaceName}. $arrival $passenger", data = listOf(row(session)))
  }

  /** "탑승 완료" — 호출 전에 바꾸기 전 상태를 넘긴다. */
  fun boarding(before: PickupSession?): GleoReply {
    val fn = GleoActions.COMPLETE_BOARDING
    return when {
      before == null -> GleoReply(fn, GleoStatus.FAIL, "There is no pickup in progress to complete.")
      before.boarded -> GleoReply(fn, GleoStatus.SUCCESS, "Boarding was already confirmed.")
      else ->
        GleoReply(
          fn,
          GleoStatus.SUCCESS,
          "Confirmed that ${before.passengerName} has boarded. Pickup guidance and location sharing have ended.",
        )
    }
  }

  /** "동승자에게 전화해 줘" — 차량에 전화 화면(ACTION_DIAL)이 있는지는 호출하는 쪽이 알려 준다. */
  fun call(session: PickupSession?, dialerAvailable: Boolean): GleoReply {
    val fn = GleoActions.CALL_PASSENGER
    return when {
      session == null -> GleoReply(fn, GleoStatus.FAIL, "There is no pickup in progress, so there is no passenger to call.")
      session.boarded -> GleoReply(fn, GleoStatus.FAIL, "${session.passengerName} has already boarded.")
      !dialerAvailable -> GleoReply(fn, GleoStatus.FAIL, "Phone calls are not available in this vehicle.")
      else -> GleoReply(fn, GleoStatus.SUCCESS, "Opening the dialer to call ${session.passengerName}.")
    }
  }

  /** 1분 미만이면 0(곧 도착), 그 외 올림. */
  private fun minutesToArrival(session: PickupSession): Int? {
    val seconds = session.remainingSeconds ?: return null
    return if (seconds < 60) 0 else ceil(seconds / 60.0).toInt()
  }

  // 전화번호는 넣지 않는다
  private fun row(session: PickupSession): Map<String, String> =
    buildMap {
      put("passengerName", session.passengerName)
      put("pickupPlace", session.pickupPlaceName)
      minutesToArrival(session)?.let { put("minutesToArrival", it.toString()) }
      put("checkedPickupLink", session.passengerCheckedLink.toString())
      put("sharingLocation", session.passengerSharingLocation.toString())
      put("boarded", session.boarded.toString())
    }
}
