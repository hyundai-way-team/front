package com.kscho.way.data.response

import com.kscho.way.data.dto.LatLngDto
import kotlinx.serialization.Serializable

@Serializable
data class PassengerStatusResponse(
  val linkOpened: Boolean = false,
  val sharing: String = SharingWire.NOT_YET,
  val location: LatLngDto? = null,
  val late: Boolean = false,
  val walk: WalkRouteResponse? = null,
)
