package com.kscho.way.gleo

import kotlin.math.ceil

object PickupGleoReplies {
  fun status(session: PickupSummary?): GleoReply {
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

  fun boarding(before: PickupSummary?): GleoReply {
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

  fun call(session: PickupSummary?, dialerAvailable: Boolean): GleoReply {
    val fn = GleoActions.CALL_PASSENGER
    return when {
      session == null -> GleoReply(fn, GleoStatus.FAIL, "There is no pickup in progress, so there is no passenger to call.")
      session.boarded -> GleoReply(fn, GleoStatus.FAIL, "${session.passengerName} has already boarded.")
      !dialerAvailable -> GleoReply(fn, GleoStatus.FAIL, "Phone calls are not available in this vehicle.")
      else -> GleoReply(fn, GleoStatus.SUCCESS, "Opening the dialer to call ${session.passengerName}.")
    }
  }

  private fun minutesToArrival(session: PickupSummary): Int? {
    val seconds = session.remainingSeconds ?: return null
    return if (seconds < 60) 0 else ceil(seconds / 60.0).toInt()
  }

  private fun row(session: PickupSummary): Map<String, String> =
    buildMap {
      put("passengerName", session.passengerName)
      put("pickupPlace", session.pickupPlaceName)
      minutesToArrival(session)?.let { put("minutesToArrival", it.toString()) }
      put("checkedPickupLink", session.passengerCheckedLink.toString())
      put("sharingLocation", session.passengerSharingLocation.toString())
      put("boarded", session.boarded.toString())
    }
}
