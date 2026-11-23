package com.kscho.way.server.zones

import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.server.kakao.PlaceFinder
import com.kscho.way.server.kakao.Region
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지정 주정차 금지구역 — 화면 안 도로의 구역만, 끝점은 (가짜) 카카오로 찾아 도로를 따라 선으로. */
class DesignatedZonesTest {
  // 위도 37.5 를 따라 동쪽으로 약 884 m 가는 2차로 길
  private val road = RoadIndex.Road("가람로", "secondary", doubleArrayOf(37.5, 37.5), doubleArrayOf(127.0, 127.01))
  private val other = RoadIndex.Road("나래로", "residential", doubleArrayOf(37.502, 37.502), doubleArrayOf(127.0, 127.01))

  private fun row(
    id: String = "np-41280-1",
    roads: List<String> = listOf("가람로"),
    desc: String = "가람약국 앞 ~ 나무사거리",
    length: Double? = 300.0,
    weekday: DayRule = DayRule("08:00", "20:00"),
    saturday: DayRule = DayRule("00:00", "00:00"),
    holiday: DayRule = DayRule("00:00", "00:00"),
  ) = NoParkingRow(id, "경기도", "고양시", "41280", roads, roads.joinToString(","), desc, length, weekday, saturday, holiday)

  /** 카카오 흉내 — 이름별 답을 정해 두고 부른 횟수를 센다. */
  private class FakeFinder(
    private val places: Map<String, List<LatLngDto>> = emptyMap(),
    private val region: Region? = Region("경기도", "고양시 덕양구", "4128110100"),
  ) : PlaceFinder {
    var calls = 0
    val asked = mutableListOf<String>()

    override suspend fun region(lat: Double, lng: Double): Region? {
      calls++
      return region
    }

    /** 카카오처럼 (lat,lng) 둘레 radiusMeters 안의 것만 준다. */
    override suspend fun keyword(query: String, lat: Double, lng: Double, radiusMeters: Int): List<LatLngDto> {
      calls++
      asked += query
      return places[query].orEmpty().filter { Geo.distanceMeters(LatLng(lat, lng), LatLng(it.lat, it.lng)) <= radiusMeters }
    }

    override suspend fun address(query: String): List<LatLngDto> {
      calls++
      asked += query
      return places[query].orEmpty()
    }
  }

  private val pharmacy = LatLngDto(37.50025, 127.002) // 길 북쪽 28 m 가게
  private val crossing = LatLngDto(37.5, 127.0054)

  private fun zones(vararg rows: NoParkingRow, finder: PlaceFinder, maxRows: Int = 24) =
    DesignatedZones(rows.toList(), RoadIndex(listOf(road, other)), finder, maxRows = maxRows)

  @Test
  fun near_drawsLineAlongTheRoadBetweenFoundEndpoints_withWeekdayHours() = runTest {
    val finder = FakeFinder(mapOf("가람약국" to listOf(pharmacy), "나무사거리" to listOf(crossing)))

    val got = zones(row(), finder = finder).near(37.5002, 127.003, 300)

    val z = got.single()
    assertEquals("np-41280-1-0", z.id)
    assertEquals("지정 주정차 금지 · 평일 08:00~20:00", z.rule)
    assertEquals(listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), z.days)
    assertEquals(480 to 1200, z.startMinute to z.endMinute)
    assertEquals(9.0, z.radiusMeters, 0.0) // 2차로(secondary) 가운데에서 길가까지
    // 가게 좌표가 아니라 길 위로, 두 끝 사이만
    assertTrue(z.path.all { it.lat == 37.5 })
    assertEquals(127.002, z.path.first().lng, 1e-6)
    assertEquals(127.0054, z.path.last().lng, 1e-6)
  }

  @Test
  fun near_doesNotCallKakao_whenNoZoneRoadIsOnScreen() = runTest {
    val finder = FakeFinder()

    assertTrue(zones(row(), finder = finder).near(37.52, 127.003, 300).isEmpty())
    assertEquals(0, finder.calls)
  }

  @Test
  fun near_skipsZonesOfAnotherSigungu() = runTest {
    val finder = FakeFinder(mapOf("가람약국" to listOf(pharmacy), "나무사거리" to listOf(crossing)), region = Region("서울특별시", "강남구", "1168010100"))

    assertTrue(zones(row(), finder = finder).near(37.5002, 127.003, 300).isEmpty())
    assertEquals(1, finder.calls) // 시군구만 묻고 끝
  }

  @Test
  fun near_skipsZone_whenAnEndpointIsFarFromTheRoad() = runTest {
    // '나무사거리' 가 300 m 북쪽 엉뚱한 곳
    val finder = FakeFinder(mapOf("가람약국" to listOf(pharmacy), "나무사거리" to listOf(LatLngDto(37.5027, 127.0054))))

    assertTrue(zones(row(), finder = finder).near(37.5002, 127.003, 300).isEmpty())
  }

  @Test
  fun near_singlePlace_drawsTheDataLengthAroundIt() = runTest {
    val finder = FakeFinder(mapOf("가람빌딩" to listOf(LatLngDto(37.5001, 127.003))))

    val z = zones(row(desc = "가람빌딩 앞", length = 60.0), finder = finder).near(37.5002, 127.003, 300).single()

    val ll = z.path.map { LatLng(it.lat, it.lng) }
    assertEquals(60.0, Geo.pathLengthMeters(ll), 1.0)
    assertEquals(127.003, (z.path.first().lng + z.path.last().lng) / 2, 1e-6)
  }

  @Test
  fun near_dropsPathFarLongerThanTheDataSays() = runTest {
    // 총연장 20 m 인데 두 끝이 500 m 떨어져 있으면 끝점을 잘못 찾은 것
    val finder = FakeFinder(mapOf("가람약국" to listOf(pharmacy), "나무사거리" to listOf(LatLngDto(37.5, 127.00765))))

    assertTrue(zones(row(length = 20.0), finder = finder).near(37.5002, 127.003, 300).isEmpty())
  }

  @Test
  fun near_allDayEveryDay_isOneZoneWithoutHours() = runTest {
    val finder = FakeFinder(mapOf("가람약국" to listOf(pharmacy), "나무사거리" to listOf(crossing)))
    val always = DayRule("00:00", "23:59")

    val z = zones(row(weekday = always, saturday = always, holiday = always), finder = finder).near(37.5002, 127.003, 300).single()

    assertEquals("np-41280-1", z.id)
    assertNull(z.days)
    assertEquals("지정 주정차 금지 · 상시", z.rule)
  }

  @Test
  fun near_looksUpNearestRoadsFirst_upToTheLimit() = runTest {
    val finder =
      FakeFinder(mapOf("가람약국" to listOf(pharmacy), "나무사거리" to listOf(crossing), "나래약국" to listOf(LatLngDto(37.5021, 127.002)), "나래사거리" to listOf(LatLngDto(37.502, 127.0054))))
    val near = row()
    val farther = row(id = "np-41280-2", roads = listOf("나래로"), desc = "나래약국 ~ 나래사거리")

    val got = zones(farther, near, finder = finder, maxRows = 1).near(37.5002, 127.003, 300)

    assertEquals(listOf("np-41280-1-0"), got.map { it.id })
    assertTrue("나래약국" !in finder.asked)
  }

  @Test
  fun near_looksUpZonesNamingThisDongFirst_onLongRoads() = runTest {
    // 한 큰길에 금지구역이 여럿 — 상세위치에 이 자리 동('백석동')이 적힌 곳을 먼저 찾는다
    val finder =
      FakeFinder(
        mapOf("백석동 가람약국" to listOf(pharmacy), "나무사거리" to listOf(crossing)),
        region = Region("경기도", "고양시 일산동구", "4128510500", listOf("백석1동", "백석동")),
      )
    val elsewhere = row(id = "np-41280-9", desc = "식사동 다른약국 ~ 다른사거리")
    val here = row(id = "np-41280-3", desc = "백석동 가람약국 ~ 나무사거리")

    val got = zones(elsewhere, here, finder = finder, maxRows = 1).near(37.5002, 127.003, 300)

    assertEquals(listOf("np-41280-3-0"), got.map { it.id })
    assertTrue("식사동 다른약국" !in finder.asked)
  }

  @Test
  fun near_pinCheckRadius_stillFindsTheFarEndOfALongZone() = runTest {
    // 2.65 km 큰길, 총연장 2 km 구역 — 핀 확인(반경 100)도 먼 끝(1.9 km)을 찾아야 지도와 판정이 같다
    val long = RoadIndex.Road("가람로", "primary", doubleArrayOf(37.5, 37.5), doubleArrayOf(127.0, 127.03))
    val finder = FakeFinder(mapOf("가람약국" to listOf(LatLngDto(37.5001, 127.001)), "나무사거리" to listOf(LatLngDto(37.5, 127.0236))))

    val z = DesignatedZones(listOf(row(length = 2_000.0)), RoadIndex(listOf(long)), finder).near(37.5, 127.002, 100).single()

    assertEquals(127.0236, z.path.last().lng, 1e-6)
  }

  @Test
  fun near_rowsThatCannotBeDrawn_doNotUseUpTheLimit() = runTest {
    val finder = FakeFinder(mapOf("가람약국" to listOf(pharmacy), "나무사거리" to listOf(crossing)))
    val noHours = row(id = "np-41280-0", weekday = DayRule("00:00", "00:00"))

    val got = zones(noHours, row(), finder = finder, maxRows = 1).near(37.5002, 127.003, 300)

    assertEquals(listOf("np-41280-1-0"), got.map { it.id })
  }

  @Test
  fun near_singlePlace_isAtMost300m_whateverTheLengthSays() = runTest {
    val finder = FakeFinder(mapOf("가람빌딩" to listOf(LatLngDto(37.5001, 127.004))))

    val z = zones(row(desc = "가람빌딩 앞", length = 2_000.0), finder = finder).near(37.5002, 127.003, 300).single()

    assertEquals(300.0, Geo.pathLengthMeters(z.path.map { LatLng(it.lat, it.lng) }), 1.0)
  }

  @Test
  fun near_doesNotFollowARoadPieceThatDoublesBack() = runTest {
    // 동쪽으로 갔다가 5 m 북쪽으로 되돌아오는 한 선(상·하행이 이어 붙은 것) — 두 끝이 서로 다른 가닥에 붙으면
    // 선을 따라 자르면 끝까지 돌아오는 긴 선이 된다 → 쓰지 않고 짧은 직선으로
    val u = RoadIndex.Road("가람로", "secondary", doubleArrayOf(37.5, 37.5, 37.50005, 37.50005), doubleArrayOf(127.0, 127.01, 127.01, 127.0))
    val finder = FakeFinder(mapOf("가람약국" to listOf(LatLngDto(37.49998, 127.002)), "나무사거리" to listOf(LatLngDto(37.50007, 127.0031))))

    val z = DesignatedZones(listOf(row()), RoadIndex(listOf(u)), finder).near(37.5002, 127.003, 300).single()

    assertEquals(2, z.path.size)
    assertTrue(Geo.pathLengthMeters(z.path.map { LatLng(it.lat, it.lng) }) < 150.0)
  }

  @Test
  fun dongStem_dropsNumbersAndSuffix() {
    assertEquals("매탄", DesignatedZones.dongStem("매탄3동"))
    assertEquals("성수", DesignatedZones.dongStem("성수1가제1동"))
    assertEquals("백석", DesignatedZones.dongStem("백석동"))
    assertNull(DesignatedZones.dongStem("중동"))
  }
}
