package com.kscho.way.gleo

import com.kscho.way.domain.model.passenger.LocationSharing
import com.kscho.way.domain.pickup.PickupPhase
import com.kscho.way.domain.pickup.PickupState

/** Gleo 답에 필요한 만큼만 추린 픽업 상태. */
data class PickupSummary(
  val passengerName: String,
  /** 전화 화면을 띄울 때만 쓴다. Gleo 응답에는 넣지 않는다. */
  val passengerPhone: String,
  val pickupPlaceName: String,
  /** 픽업 위치까지 남은 시간(초). 모르면 null, 정차했으면 0. */
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
