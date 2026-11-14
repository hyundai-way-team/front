package com.kscho.way.server

import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.ZoneDto
import com.kscho.way.server.kakao.PlaceFinder
import com.kscho.way.server.kakao.Region
import com.kscho.way.server.zones.DayRule
import com.kscho.way.server.zones.DesignatedZones
import com.kscho.way.server.zones.NoParkingRow
import com.kscho.way.server.zones.RoadIndex
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** /api/stop-zones — 버스정류장 10m 에 카카오 키가 있으면 지정 주정차 금지구역이 더해진다. */
class StopZonesRouteTest {
  private val road = RoadIndex.Road("신촌로", "primary", doubleArrayOf(37.5568, 37.5568), doubleArrayOf(126.935, 126.95))
  private val row =
    NoParkingRow(
      "np-11410-1", "서울특별시", "서대문구", "11410", listOf("신촌로"), "신촌로", "가람약국 ~ 나무사거리", 200.0,
      DayRule("00:00", "23:59"), DayRule("00:00", "23:59"), DayRule("00:00", "23:59"),
    )

  private val finder =
    object : PlaceFinder {
      override suspend fun region(lat: Double, lng: Double) = Region("서울특별시", "서대문구", "1141010100")

      override suspend fun keyword(query: String, lat: Double, lng: Double, radiusMeters: Int) =
        when (query) {
          "가람약국" -> listOf(LatLngDto(37.5569, 126.9415))
          "나무사거리" -> listOf(LatLngDto(37.5568, 126.9437))
          else -> emptyList()
        }

      override suspend fun address(query: String) = emptyList<LatLngDto>()
    }

  @Test
  fun stopZones_addsDesignatedZones_whenKakaoIsOn() = testApplication {
    val deps = createDeps("http://localhost:8080", null, designated = DesignatedZones(listOf(row), RoadIndex(listOf(road)), finder))
    application { wayModule(deps) }

    val zones = WayJson.decodeFromString(ListSerializer(ZoneDto.serializer()), client.get("/api/stop-zones?lat=37.556783&lng=126.942649&radius=200").bodyAsText())

    assertTrue(zones.any { it.id.startsWith("bus-") })
    val np = zones.single { it.id.startsWith("np-") }
    assertEquals("np-11410-1", np.id)
    assertEquals("지정 주정차 금지 · 상시", np.rule)
    assertEquals(12.0, np.radiusMeters, 0.0)
    assertTrue(np.path.size >= 2)
  }

  @Test
  fun stopZones_limitsDesignatedLookupsPerClientIp() = testApplication {
    val deps = createDeps("http://localhost:8080", null, designated = DesignatedZones(listOf(row), RoadIndex(listOf(road)), finder), designatedGate = RateGate(1, 60_000))
    application { wayModule(deps) }
    val url = "/api/stop-zones?lat=37.556783&lng=126.942649&radius=200"

    val first = client.get(url) { header("X-Forwarded-For", "203.0.113.7") }.bodyAsText()
    val second = client.get(url) { header("X-Forwarded-For", "203.0.113.7") }.bodyAsText()
    val other = client.get(url) { header("X-Forwarded-For", "198.51.100.2") }.bodyAsText()

    assertTrue(first.contains("\"np-"))
    // 같은 IP 가 넘치면 버스정류장만(카카오를 부르지 않는다)
    assertTrue(second.contains("\"bus-") && !second.contains("\"np-"))
    assertTrue(other.contains("\"np-"))
  }

  @Test
  fun stopZones_isBusStopsOnly_withoutKakaoKey() = testApplication {
    application { wayModule(createDeps("http://localhost:8080", null)) }

    val body = client.get("/api/stop-zones?lat=37.556783&lng=126.942649&radius=200").bodyAsText()

    assertTrue(body.contains("\"bus-") && !body.contains("\"np-"))
  }
}
