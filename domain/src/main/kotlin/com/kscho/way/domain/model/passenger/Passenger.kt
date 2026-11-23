package com.kscho.way.domain.model.passenger

import com.kscho.way.domain.model.geo.LatLng

enum class LocationSharing {
  NOT_YET,
  SHARING,
  DECLINED,
  ENDED,
}

data class PassengerStatus(
  val linkOpened: Boolean = false,
  val sharing: LocationSharing = LocationSharing.NOT_YET,
  val location: LatLng? = null,
  val late: Boolean = false,
  val walk: WalkRoute? = null,
)

data class WalkRoute(val path: List<LatLng>, val meters: Int, val seconds: Int)
