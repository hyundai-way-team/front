package com.kscho.way.domain.model.geo

import kotlin.math.roundToInt

data class MapArea(val center: LatLng, val radiusMeters: Int) {
  fun covers(center: LatLng, visibleRadiusMeters: Int): Boolean = Geo.distanceMeters(this.center, center) + visibleRadiusMeters <= radiusMeters

  companion object {
    private const val MARGIN = 1.5
    private const val MIN_METERS = 300
    private const val MAX_METERS = 5_000

    fun around(center: LatLng, visibleRadiusMeters: Int) =
      MapArea(center, (visibleRadiusMeters * MARGIN).roundToInt().coerceIn(MIN_METERS, MAX_METERS))
  }
}
