package com.kscho.way.gleo

import com.kscho.way.domain.model.passenger.LocationSharing
import com.kscho.way.domain.pickup.PickupPhase
import com.kscho.way.domain.pickup.PickupState

data class PickupSummary(
  val passengerName: String,
  val passengerPhone: String,
  val pickupPlaceName: String,
  val remainingSeconds: Int?,
  val passengerCheckedLink: Boolean,
  val passengerSharingLocation: Boolean,
  val boarded: Boolean = false,
)

fun PickupState.toGleoSummary() =
  PickupSummary(
    passengerName = plan.passenger.name,
    passengerPhone = plan.passenger.phone,
    pickupPlaceName = plan.pickupPoint.description.title,
    remainingSeconds = if (phase == PickupPhase.DRIVING) remainingSeconds else 0,
    passengerCheckedLink = passenger.linkOpened,
    passengerSharingLocation = passenger.sharing == LocationSharing.SHARING,
    boarded = isBoarded,
  )
