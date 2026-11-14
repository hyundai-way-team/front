package com.kscho.way.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지도에 그리는 정차 금지 구간은 픽업 위치 둘레만. */
class PickupZonesTest {
  private val pin = LatLng(37.5567, 126.942)

  private fun stop(id: String, north: Double, east: Double = 0.0) = RestrictedZone(id, id, "절대 주정차 금지 · 상시", listOf(Geo.offset(pin, east = east, north = north)))

  @Test
  fun around_keepsOnlyZonesNearThePickup() {
    val near = stop("near", north = 120.0)
    val edge = stop("edge", north = 305.0) // 정류장 둘레 10 m 가 300 m 안에 걸친다
    val far = stop("far", north = 600.0)

    assertEquals(listOf("near", "edge"), PickupZones.around(pin, listOf(near, edge, far)).map { it.id })
  }

  @Test
  fun around_keepsALongZoneThatPassesNearby_evenIfItsEndsAreFar() {
    // 지정 금지구역 선: 두 끝은 700 m 밖이지만 픽업 위치 50 m 앞을 지나간다
    val road = RestrictedZone("road", "신촌로", "주정차 금지", listOf(Geo.offset(pin, east = -700.0, north = 50.0), Geo.offset(pin, east = 700.0, north = 50.0)), radiusMeters = 8.0)

    assertEquals(listOf("road"), PickupZones.around(pin, listOf(road)).map { it.id })
  }

  @Test
  fun needsReload_onlyWhenThePickupCircleLeavesWhatWasLoaded() {
    val loaded = PickupZones.areaFor(pin)

    assertFalse(PickupZones.needsReload(loaded, Geo.offset(pin, east = 0.0, north = 100.0)))
    assertTrue(PickupZones.needsReload(loaded, Geo.offset(pin, east = 0.0, north = 400.0)))
    assertTrue(PickupZones.needsReload(null, pin))
  }
}
