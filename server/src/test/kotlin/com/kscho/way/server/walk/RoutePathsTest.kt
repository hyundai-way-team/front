package com.kscho.way.server.walk

import com.kscho.way.data.dto.LatLngDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 차 길 다듬기 — 지나온 부분 자르기 · 길에서 벗어난 거리 · 점 줄이기. */
class RoutePathsTest {
  // 남쪽에서 북쪽으로 곧게 가다 동쪽으로 꺾는 길(약 111 m 간격)
  private val a = LatLngDto(37.550, 126.940)
  private val b = LatLngDto(37.551, 126.940)
  private val c = LatLngDto(37.552, 126.940)
  private val d = LatLngDto(37.552, 126.942)
  private val path = listOf(a, b, c, d)

  @Test
  fun remainingFrom_dropsWhatTheCarAlreadyPassed() {
    val car = LatLngDto(37.5515, 126.94001) // b 와 c 사이
    assertEquals(listOf(car, c, d), RoutePaths.remainingFrom(path, car))
  }

  @Test
  fun remainingFrom_beforeTheStart_keepsTheWholeRoute() {
    val car = LatLngDto(37.5499, 126.940)
    assertEquals(listOf(car, b, c, d), RoutePaths.remainingFrom(path, car))
  }

  @Test
  fun distanceToPath_measuresHowFarTheCarIsOffTheRoute() {
    assertEquals(0.0, RoutePaths.distanceToPath(LatLngDto(37.5505, 126.940), path), 0.5)
    // 경도 0.001° ≈ 88 m(위도 37.55)
    assertEquals(88.0, RoutePaths.distanceToPath(LatLngDto(37.5505, 126.941), path), 2.0)
  }

  @Test
  fun simplify_dropsPointsOnAStraightLine_butKeepsTurns() {
    val dense = (0..20).map { LatLngDto(37.550 + it * 0.0001, 126.940) } + LatLngDto(37.552, 126.942)
    val simple = RoutePaths.simplify(dense, toleranceMeters = 3.0)
    assertEquals(listOf(dense.first(), dense[20], dense.last()), simple)
  }

  @Test
  fun simplify_keepsAWiggleBiggerThanTheTolerance() {
    val wiggle = listOf(a, LatLngDto(37.5505, 126.9401), b) // 가운데 점이 약 9 m 옆으로
    assertTrue(RoutePaths.simplify(wiggle, toleranceMeters = 3.0).size == 3)
    assertEquals(2, RoutePaths.simplify(wiggle, toleranceMeters = 20.0).size)
  }
}
