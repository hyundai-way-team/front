package com.kscho.way.domain.model.pickup

import com.kscho.way.domain.model.pickup.PickupPoint
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.model.route.RoutePreview
data class PickupPointCheck(
  val point: PickupPoint,
  val route: RoutePreview,
  val arrivalAtMillis: Long,
  val restriction: StopRestriction,
) {
  val canConfirm: Boolean
    get() = restriction !is StopRestriction.NoStopping
}
