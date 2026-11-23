package com.kscho.way.domain.model.pickup

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.model.route.RoutePreview
import com.kscho.way.domain.model.vehicle.MyVehicle

data class PickupPoint(
  val location: LatLng,
  val description: LocationDescription,
  val distanceFromPlaceMeters: Int?,
)

data class PickupPlan(
  val passenger: Contact,
  val place: Place?,
  val pickupPoint: PickupPoint,
  val restriction: StopRestriction,
  val route: RoutePreview,
  val vehicle: MyVehicle,
  val destination: Place? = null,
)

enum class BoardingSource {
  DOOR,
  MANUAL,
  VOICE,
}

data class PickupOutcome(
  val boarded: Boolean,
  val boardedAtMillis: Long?,
  val waitedSeconds: Int?,
  val pickupLocationChanges: Int,
  val boardingSource: BoardingSource?,
)

data class PickupRecord(
  val id: String,
  val passenger: Contact,
  val place: Place?,
  val pickupPoint: PickupPoint,
  val boarded: Boolean,
  val pickupLocationChanges: Int,
  val waitedSeconds: Int?,
  val endedAtMillis: Long,
)
