package com.kscho.way.server.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 정차 금지 구간 = 실제 버스정류장 위치(국토교통부 공공데이터) 둘레 10m — 도로교통법 제32조. */
class BusStopZonesTest {
  private val csv =
    """
    id,name,lat,lng
    S1,신촌오거리.현대백화점,37.556720,126.942400
    S2,연세대앞,37.559900,126.937800
    S3,멀리,37.600000,127.000000
    """.trimIndent()

  @Test
  fun near_returnsStopsInRadius_asTenMeterAlwaysOnZones() {
    val zones = BusStopZones.parse(csv.byteInputStream())

    val near = zones.near(37.556783, 126.942649, 200)

    assertEquals(listOf("bus-S1"), near.map { it.id })
    val z = near.single()
    assertEquals("신촌오거리.현대백화점 버스정류장", z.name)
    assertEquals(10.0, z.radiusMeters, 0.0)
    assertNull(z.days) // 요일·시간 없음 = 상시
    assertEquals(1, z.path.size)
    assertEquals(37.556720, z.path.single().lat, 1e-9)
  }

  @Test
  fun bundledCapitalData_findsRealStopsAroundSinchon() {
    val near = BusStopZones.bundled().near(37.556783, 126.942649, 300)
    assertTrue(near.isNotEmpty())
    assertTrue(near.all { it.id.startsWith("bus-") })
  }
}
