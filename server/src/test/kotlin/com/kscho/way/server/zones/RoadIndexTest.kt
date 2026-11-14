package com.kscho.way.server.zones

import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 금지구역 도로 선(OSM) — 화면 안 도로 고르기 · 점을 도로 위에 내리기 · 도로를 따라 자르기. */
class RoadIndexTest {
  // 동쪽으로 0.004°(약 354 m) 간 뒤 북쪽으로 0.003°(약 334 m) 꺾이는 길
  private val bent = RoadIndex.Road("가람로", "secondary", doubleArrayOf(37.5, 37.5, 37.503), doubleArrayOf(127.0, 127.004, 127.004))
  private val far = RoadIndex.Road("먼길", "residential", doubleArrayOf(37.6, 37.6), doubleArrayOf(127.1, 127.101))

  @Test
  fun snap_putsPointOnTheRoad_withDistanceAndWalkedLength() {
    val s = RoadIndex.snap(bent, 37.5003, 127.002)

    assertEquals(33.4, s.distance, 1.0)
    assertEquals(176.8, s.along, 1.5)
    assertEquals(37.5, s.point.lat, 1e-9)
    assertEquals(127.002, s.point.lng, 1e-9)
  }

  @Test
  fun cut_followsTheRoadAroundTheCorner() {
    val part = RoadIndex.cut(bent, 300.0, 400.0)

    assertEquals(3, part.size) // 시작 · 모퉁이 · 끝
    assertEquals(127.004, part[1].lng, 1e-9)
    assertEquals(37.5, part[1].lat, 1e-9)
    val ll = part.map { LatLng(it.lat, it.lng) }
    assertEquals(100.0, Geo.pathLengthMeters(ll), 1.5)
    // 순서를 거꾸로 줘도 같은 부분
    assertEquals(part, RoadIndex.cut(bent, 400.0, 300.0))
  }

  @Test
  fun cut_clampsToTheRoadEnds() {
    val total = RoadIndex.length(bent)
    val part = RoadIndex.cut(bent, -50.0, total + 50.0)
    assertEquals(3, part.size)
    assertEquals(37.503, part.last().lat, 1e-9)
  }

  @Test
  fun near_returnsRoadsCrossingTheCircle_nearestFirst() {
    val index = RoadIndex(listOf(far, bent))

    val near = index.near(37.5005, 127.002, 100.0)

    assertEquals(listOf("가람로"), near.map { it.first.name })
    assertTrue(index.near(37.5005, 127.002, 30.0).isEmpty())
  }

  @Test
  fun parse_skipsCommentsAndBrokenLines() {
    val text =
      """
      # 이름	종류	점들
      가람로	secondary	37.50000,127.00000 37.50000,127.00400
      부서진줄	residential	37.5,127.0
      이름만
      """.trimIndent()

    val index = RoadIndex.parse(text.byteInputStream())

    assertEquals(1, index.size)
    assertEquals(9.0, index.near(37.5, 127.002, 10.0).single().first.halfWidthMeters, 0.0)
  }

  @Test
  fun bundledCapitalRoads_coverZoneRoads() {
    val index = RoadIndex.bundled()
    assertTrue(index.size > 1_000)
  }
}
