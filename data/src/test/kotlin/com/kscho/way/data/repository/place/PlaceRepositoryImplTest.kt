package com.kscho.way.data.repository.place

import com.kscho.way.data.dataSource.local.place.AreaLevel
import com.kscho.way.data.dataSource.local.place.LocationWording
import com.kscho.way.data.dataSource.local.place.PlaceIndex
import com.kscho.way.data.dataSource.local.place.RoadClass
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.LocationDescription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRepositoryImplTest {
  private val origin = LatLng(37.5567, 126.9420)

  private fun at(east: Double, north: Double) = Geo.offset(origin, east = east, north = north)

  private fun line(y: Double) = listOf(at(-600.0, y), at(0.0, y), at(600.0, y))

  private val areas =
    listOf(
      PlaceIndex.Area("서울특별시", AreaLevel.CITY, "", at(2_000.0, 1_000.0)),
      PlaceIndex.Area("마포구", AreaLevel.BOROUGH, "서울", at(-1_500.0, -500.0)),
      PlaceIndex.Area("연남동", AreaLevel.QUARTER, "서울 마포구", at(-900.0, 600.0)),
      PlaceIndex.Area("창천동", AreaLevel.QUARTER, "서울 서대문구", at(100.0, 100.0)),
    )
  private val yeonnam = 2
  private val changcheon = 3

  private fun poi(name: String, category: String, subclass: String, east: Double, north: Double, area: Int = changcheon, street: Boolean = true) =
    PlaceIndex.Poi(name, category, subclass, at(east, north), area, street)

  private val pois =
    listOf(
      poi("신촌자이엘라 버스정류장", "bus", "bus_stop", 60.0, 16.0),
      poi("신촌자이엘라 버스정류장", "bus", "bus_stop", 60.0, -16.0),
      poi("할매국밥", "restaurant", "restaurant", -95.0, 12.0),
      poi("2층 카페", "cafe", "cafe", -100.0, -20.0, street = false),
      poi("스타벅스 연세대점", "cafe", "cafe", 10.0, 20.0),
      poi("연세대학교 신촌캠퍼스", "college", "university", 250.0, 300.0),
      poi("연세대앞 버스정류장", "bus", "bus_stop", 210.0, 60.0),
      poi("연세대학교 공학관", "office", "educational_institution", 260.0, 250.0),
      poi("세븐일레븐 신촌점", "shop", "convenience", 500.0, 30.0),
      poi("세븐일레븐 연희점", "shop", "convenience", -50.0, 30.0),
      poi("Starbucks Reserve", "cafe", "cafe", 400.0, -200.0),
      poi("동진시장", "grocery", "marketplace", -800.0, 650.0, area = yeonnam),
      poi("연남 김밥", "fast_food", "fast_food", -850.0, 640.0, area = yeonnam),
    ) + (1..12).map { poi("편의점 $it", "shop", "convenience", -300.0 + it * 40.0, 200.0) }

  private val roads =
    listOf(
      PlaceIndex.Road("신촌로", RoadClass.PRIMARY, line(8.0)),
      PlaceIndex.Road("신촌로", RoadClass.PRIMARY, line(-8.0)),
      PlaceIndex.Road("연세로", RoadClass.SECONDARY, listOf(at(200.0, 10.0), at(200.0, 400.0))),
      PlaceIndex.Road("연희로11길", RoadClass.MINOR, line(120.0)),
    )

  private val index = PlaceIndex.of(areas, pois, roads)
  private var here: LatLng? = origin
  private val repo = PlaceRepositoryImpl(index, origin = { here })

  @Test
  fun search_prefixBeforeContains_thenNearest() = runTest {
    val names = repo.search("연세대").map { it.name }
    assertEquals(listOf("연세대앞 버스정류장", "연세대학교 신촌캠퍼스", "연세대학교 공학관", "스타벅스 연세대점"), names)
  }

  @Test
  fun search_samePointDifferentNames_getDistinctIds() = runTest {
    val twin = PlaceRepositoryImpl(PlaceIndex.of(areas, listOf(poi("GS25", "shop", "convenience", 0.0, 0.0), poi("GS25 신촌점", "shop", "convenience", 0.0, 0.0)), roads))
    val ids = twin.search("GS25").map { it.id }
    assertEquals(2, ids.size)
    assertEquals(ids.size, ids.toSet().size)
  }

  @Test
  fun search_ignoresSpacesAndCase() = runTest {
    assertEquals(repo.search("연세대").map { it.id }, repo.search(" 연세 대 ").map { it.id })
    assertEquals(listOf("Starbucks Reserve"), repo.search("starbucks").map { it.name })
    assertEquals(listOf("Starbucks Reserve"), repo.search("STARBUCKSRESERVE").map { it.name })
  }

  @Test
  fun search_nearestFirst_followsOrigin() = runTest {
    assertEquals(listOf("세븐일레븐 연희점", "세븐일레븐 신촌점"), repo.search("세븐일레븐").map { it.name })
    here = at(480.0, 30.0)
    assertEquals(listOf("세븐일레븐 신촌점", "세븐일레븐 연희점"), repo.search("세븐일레븐").map { it.name })
  }

  @Test
  fun search_atMostEight_andSameNameNearbyOnce() = runTest {
    assertEquals(8, repo.search("편의점").size)
    assertEquals(listOf("신촌자이엘라 버스정류장"), repo.search("신촌자이엘라").map { it.name })
  }

  @Test
  fun search_areaNameAndAddress() = runTest {
    val result = repo.search("연남동")
    assertEquals("연남동", result.first().name)
    assertEquals("서울 마포구", result.first().address)
    assertEquals(setOf("동진시장", "연남 김밥"), result.drop(1).map { it.name }.toSet())
    assertTrue(result.drop(1).all { it.address == "서울 마포구 연남동" })
    assertEquals(listOf("연남 김밥"), repo.search("마포구 김밥").map { it.name })
  }

  @Test
  fun search_poiAddressAndLocation() = runTest {
    val campus = repo.search("연세대학교 신촌").single()
    assertEquals("서울 서대문구 창천동", campus.address)
    assertTrue(Geo.distanceMeters(campus.location, at(250.0, 300.0)) < 1.0)
    assertTrue(repo.search("  ").isEmpty())
    assertTrue(repo.search("없는장소").isEmpty())
  }

  @Test
  fun search_withoutOrigin_easyPlacesFirst() = runTest {
    here = null
    assertEquals("연세대학교 신촌캠퍼스", repo.search("연세대").first().name)
    val failing = PlaceRepositoryImpl(index, origin = { error("위치 없음") })
    assertEquals("연세대학교 신촌캠퍼스", failing.search("연세대").first().name)
  }

  private suspend fun describe(east: Double, north: Double): LocationDescription = repo.describe(at(east, north))

  @Test
  fun describe_busStopOnSameSide() = runTest {
    assertEquals(LocationDescription("신촌자이엘라 버스정류장 앞", "신촌로 · 도로 북쪽 인도"), describe(55.0, 18.0))
    assertEquals(LocationDescription("신촌자이엘라 버스정류장 앞", "신촌로 · 도로 남쪽 인도"), describe(55.0, -18.0))
  }

  @Test
  fun describe_skipsAcrossTheRoadAndIndoor() = runTest {
    assertEquals(LocationDescription("신촌로 근처", "신촌로 · 도로 남쪽 인도"), describe(-100.0, -17.0))
  }

  @Test
  fun describe_betweenCarriageways_isOnRoad() = runTest {
    assertEquals(LocationDescription("신촌로 도로 위", "신촌로 · 차로 위예요 · 길가로 옮겨 주세요"), describe(0.0, 0.0))
  }

  @Test
  fun describe_eastWestSideOfNorthSouthRoad() = runTest {
    assertEquals(LocationDescription("연세로 근처", "연세로 · 도로 동쪽 인도"), describe(212.0, 150.0))
    assertEquals(LocationDescription("연세로 근처", "연세로 · 도로 서쪽 인도"), describe(189.0, 160.0))
  }

  @Test
  fun describe_minorRoadSide_andFartherAway() = runTest {
    assertEquals(LocationDescription("연희로11길 근처", "연희로11길 · 도로 북쪽 길가"), describe(-200.0, 125.0))
    assertEquals(LocationDescription("신촌로 근처", "신촌로 북쪽으로 약 40m · 골목 안일 수 있어요"), describe(-200.0, 60.0))
  }

  @Test
  fun describe_noRoadNearby() = runTest {
    assertEquals(LocationDescription("지도에서 고른 위치", "큰길에서 떨어진 곳 · 골목 안일 수 있어요"), describe(3_000.0, 3_000.0))
    assertEquals(LocationDescription("지도에서 고른 위치", "연남동 · 큰길에서 떨어진 곳 · 골목 안일 수 있어요"), describe(-900.0, 900.0))
  }

  @Test
  fun compass_dominantAxis() {
    assertEquals("북쪽", LocationWording.compass(0.3, 0.9))
    assertEquals("남쪽", LocationWording.compass(-0.3, -0.9))
    assertEquals("동쪽", LocationWording.compass(0.9, -0.3))
    assertEquals("서쪽", LocationWording.compass(-0.9, 0.3))
  }
}
