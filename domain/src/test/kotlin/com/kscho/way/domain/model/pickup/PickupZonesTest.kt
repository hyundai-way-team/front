package com.kscho.way.domain.model.pickup

import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.RestrictedZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupZonesTest {
  private val pin = LatLng(37.5567, 126.942)

  private fun stop(id: String, north: Double, east: Double = 0.0) = RestrictedZone(id, id, "절대 주정차 금지 · 상시", listOf(Geo.offset(pin, east = east, north = north)))

  @Test
  fun around_keepsOnlyZonesNearThePickup() {
    val near = stop("near", north = 120.0)
    val edge = stop("edge", north = 305.0)
    val far = stop("far", north = 600.0)

    assertEquals(listOf("near", "edge"), PickupZones.around(pin, listOf(near, edge, far)).map { it.id })
  }

  @Test
  fun around_keepsALongZoneThatPassesNearby_evenIfItsEndsAreFar() {
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
