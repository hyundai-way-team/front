package com.kscho.way.domain.model

import kotlin.math.roundToInt

/**
 * 지도에 그릴 정차 금지 구간을 받아 둔 곳(가운데 · 반지름). 핀을 옮겨 화면이 이 밖으로 나가면 다시 받는다(D-04).
 * 조금 옮길 때마다 서버에 묻지 않게 화면보다 넉넉히([MARGIN]배) 받는다. 서버 상한은 5 km.
 */
data class MapArea(val center: LatLng, val radiusMeters: Int) {
  /** [center] 를 가운데로 반지름 [visibleRadiusMeters] 인 화면이 이 안에 다 드는지. */
  fun covers(center: LatLng, visibleRadiusMeters: Int): Boolean = Geo.distanceMeters(this.center, center) + visibleRadiusMeters <= radiusMeters

  companion object {
    private const val MARGIN = 1.5
    private const val MIN_METERS = 300
    private const val MAX_METERS = 5_000

    fun around(center: LatLng, visibleRadiusMeters: Int) =
      MapArea(center, (visibleRadiusMeters * MARGIN).roundToInt().coerceIn(MIN_METERS, MAX_METERS))
  }
}
