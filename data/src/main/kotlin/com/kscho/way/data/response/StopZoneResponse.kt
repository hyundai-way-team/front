package com.kscho.way.data.response

import com.kscho.way.data.dto.LatLngDto
import kotlinx.serialization.Serializable

@Serializable
data class StopZoneResponse(
  val id: String,
  val name: String,
  val rule: String,
  val path: List<LatLngDto>,
  val radiusMeters: Double = 10.0,
  val days: List<String>? = null,
  val startMinute: Int? = null,
  val endMinute: Int? = null,
)
