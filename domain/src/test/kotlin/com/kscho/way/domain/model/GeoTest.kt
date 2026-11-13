package com.kscho.way.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
  private val origin = LatLng(37.5555, 126.9390)

  @Test
  fun offsetThenDistance_roundTrips() {
    val north = Geo.offset(origin, east = 0.0, north = 40.0)
    assertEquals(40.0, Geo.distanceMeters(origin, north), 0.1)
    val east = Geo.offset(origin, east = 300.0, north = 0.0)
    assertEquals(300.0, Geo.distanceMeters(origin, east), 0.5)
  }

  @Test
  fun pointAlong_walksThroughCorners() {
    val corner = Geo.offset(origin, east = 100.0, north = 0.0)
    val end = Geo.offset(corner, east = 0.0, north = 100.0)
    val path = listOf(origin, corner, end)
    assertEquals(200.0, Geo.pathLengthMeters(path), 0.5)
    // 150m = 모퉁이에서 북쪽으로 50m
    assertEquals(0.0, Geo.distanceMeters(Geo.pointAlong(path, 150.0), Geo.offset(corner, 0.0, 50.0)), 0.5)
    assertEquals(end, Geo.pointAlong(path, 999.0))
  }

  @Test
  fun distanceToPath_isPerpendicularDistance() {
    val a = Geo.offset(origin, east = -50.0, north = 0.0)
    val b = Geo.offset(origin, east = 50.0, north = 0.0)
    val p = Geo.offset(origin, east = 10.0, north = 12.0)
    assertEquals(12.0, Geo.distanceToPathMeters(p, listOf(a, b)), 0.2)
  }

  @Test
  fun circle_ringAroundCenterAtRadius() {
    // 점 하나인 정차 금지 구간(버스정류장 10m)을 지도에 원으로 그린다
    val ring = Geo.circle(origin, radiusMeters = 10.0, steps = 24)
    assertEquals(25, ring.size) // 닫힌 고리: 처음 점으로 돌아온다
    assertEquals(ring.first(), ring.last())
    ring.forEach { assertEquals(10.0, Geo.distanceMeters(origin, it), 0.05) }
  }
}
