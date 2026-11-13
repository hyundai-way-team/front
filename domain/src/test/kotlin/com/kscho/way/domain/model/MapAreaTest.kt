package com.kscho.way.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지도에 그릴 정차 금지 구간을 어디까지 받아 뒀는지 — 핀을 옮겨 화면이 그 밖으로 나가면 다시 받는다. */
class MapAreaTest {
  private val sinchon = LatLng(37.5567, 126.9420)

  @Test
  fun loadsABitMoreThanTheScreen_soSmallMovesNeedNoQuery() {
    val loaded = MapArea.around(sinchon, visibleRadiusMeters = 200)
    assertEquals(300, loaded.radiusMeters) // 화면의 1.5배
    // 핀을 50 m 옮겨도 화면(200 m)은 받아 둔 300 m 안
    assertTrue(loaded.covers(Geo.offset(sinchon, 50.0, 0.0), 200))
  }

  @Test
  fun movingOutOrZoomingOut_needsANewQuery() {
    val loaded = MapArea.around(sinchon, visibleRadiusMeters = 200)
    assertFalse(loaded.covers(Geo.offset(sinchon, 150.0, 0.0), 200)) // 화면 끝이 받아 둔 곳 밖
    assertFalse(loaded.covers(sinchon, 400)) // 축소
  }

  @Test
  fun queryRadius_staysWithinServerLimit() {
    assertEquals(5_000, MapArea.around(sinchon, visibleRadiusMeters = 9_000).radiusMeters)
    assertEquals(300, MapArea.around(sinchon, visibleRadiusMeters = 20).radiusMeters)
  }
}
