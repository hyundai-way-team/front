package com.kscho.way.domain.model.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAreaTest {
  private val sinchon = LatLng(37.5567, 126.9420)

  @Test
  fun loadsABitMoreThanTheScreen_soSmallMovesNeedNoQuery() {
    val loaded = MapArea.around(sinchon, visibleRadiusMeters = 200)
    assertEquals(300, loaded.radiusMeters)
    assertTrue(loaded.covers(Geo.offset(sinchon, 50.0, 0.0), 200))
  }

  @Test
  fun movingOutOrZoomingOut_needsANewQuery() {
    val loaded = MapArea.around(sinchon, visibleRadiusMeters = 200)
    assertFalse(loaded.covers(Geo.offset(sinchon, 150.0, 0.0), 200))
    assertFalse(loaded.covers(sinchon, 400))
  }

  @Test
  fun queryRadius_staysWithinServerLimit() {
    assertEquals(5_000, MapArea.around(sinchon, visibleRadiusMeters = 9_000).radiusMeters)
    assertEquals(300, MapArea.around(sinchon, visibleRadiusMeters = 20).radiusMeters)
  }
}
