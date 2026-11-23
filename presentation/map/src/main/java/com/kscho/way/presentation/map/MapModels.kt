package com.kscho.way.presentation.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.kscho.way.domain.model.geo.LatLng

enum class PinKind {
  CAR,
  PICKUP,
  PICKUP_BLOCKED,
  PLACE,
  GHOST,
  GHOST_BLOCKED,
  PASSENGER,
}

data class MapPin(val location: LatLng, val kind: PinKind, val label: String? = null)

data class MapLine(val path: List<LatLng>, val color: Color, val widthDp: Float = 6f, val dashed: Boolean = false)
