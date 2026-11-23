package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.model.route.TurnInstruction
import kotlin.math.roundToInt

enum class PickupPhase {
  DRIVING,
  WAITING,
  BOARDED,
}

data class PickupState(
  val id: String,
  val shareUrl: String,
  val plan: PickupPlan,
  val startedAtMillis: Long,
  val arrivalAtMillis: Long,
  val phase: PickupPhase = PickupPhase.DRIVING,
  val carLocation: LatLng? = null,
  val remainingSeconds: Int? = plan.route.durationSeconds,
  val remainingMeters: Int? = plan.route.distanceMeters,
  val notifiedArrivalAtMillis: Long = arrivalAtMillis,
  val etaChangeNotices: Int = 0,
  val passenger: PassengerStatus = PassengerStatus(),
  val arrivingSoonSent: Boolean = false,
  val arrivedAtMillis: Long? = null,
  val callOffered: Boolean = false,
  val boardedAtMillis: Long? = null,
  val boardingSource: BoardingSource? = null,
  val lastAnnouncement: SpokenAnnouncement? = null,
  val nextTurn: TurnInstruction? = null,
  val nextCanceled: Boolean = false,
  val destinationTripSeconds: Int? = null,
  val destinationArrivalAtMillis: Long? = null,
) {
  val isBoarded: Boolean
    get() = phase == PickupPhase.BOARDED

  fun waitRemainingMillis(nowMillis: Long): Long? {
    val arrived = arrivedAtMillis ?: return null
    if (phase != PickupPhase.WAITING) return null
    return (arrived + PickupPolicy.WAIT_SECONDS * 1000L - nowMillis).coerceAtLeast(0L)
  }

  fun connectRemainingMillis(nowMillis: Long): Long? {
    val boarded = boardedAtMillis ?: return null
    if (!isBoarded || nextCanceled) return null
    return (boarded + PickupPolicy.CONNECT_AFTER_BOARDING_SECONDS * 1000L - nowMillis).coerceAtLeast(0L)
  }

  val passengerDistanceMeters: Int?
    get() = passenger.location?.let { Geo.distanceMeters(it, plan.pickupPoint.location).roundToInt() }

  val waitedSeconds: Int?
    get() {
      val arrived = arrivedAtMillis ?: return null
      val boarded = boardedAtMillis ?: return null
      return ((boarded - arrived) / 1000).toInt().coerceAtLeast(0)
    }
}
