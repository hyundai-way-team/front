package com.kscho.way.ui.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-06·D-07 주행 지도 확대 — 픽업 위치에 가까워질수록 확대하고, 픽업 위치는 카드에 안 가린 곳 안에 든다. */
class DriveCameraTest {
  // 지도 1300×920dp, 왼쪽 472dp 는 상태 카드가 가림 → 차를 둔 가운데에서 보이는 가장자리까지 가장 짧은 거리 = 414dp
  private val width = 1300f
  private val height = 920f
  private val covered = 472f

  private fun zoom(meters: Double) = DriveCamera.metersPerDp(meters, width, height, covered)

  @Test
  fun far_keepsTheWideDrivingZoom() {
    assertEquals(DriveCamera.FAR_METERS_PER_DP, zoom(5_000.0))
    assertEquals(DriveCamera.FAR_METERS_PER_DP, zoom(800.0))
  }

  @Test
  fun closer_zoomsInStepByStep() {
    val steps = listOf(600.0, 400.0, 250.0, 150.0).map(::zoom)
    assertTrue("가까워질수록 1dp 가 덮는 거리가 줄어야(=확대) 한다: $steps", steps.zipWithNext().all { (a, b) -> b < a })
  }

  @Test
  fun approaching_keepsThePickupInsideTheUncoveredView() {
    // 차(가운데) → 픽업 위치가 보이는 가장 짧은 반폭(414dp)의 80% 자리에 온다
    for (meters in listOf(450.0, 300.0, 150.0)) {
      val dp = meters / zoom(meters)
      assertEquals("$meters m", 414.0 * 0.8, dp, 0.5)
    }
  }

  @Test
  fun arriving_stopsAtTheWaitingScreenZoom() {
    // 정차 화면(D-08)과 같은 확대에서 멈춘다 — 도착할 때 확대가 튀지 않게
    assertEquals(DriveCamera.NEAR_METERS_PER_DP, zoom(60.0))
    assertEquals(DriveCamera.NEAR_METERS_PER_DP, zoom(0.0))
  }

  @Test
  fun narrowerUncoveredView_zoomsInLater() {
    // 같은 거리라도 보이는 곳이 좁으면 더 넓게(덜 확대) 잡아 픽업 위치가 화면에 남는다
    assertTrue(DriveCamera.metersPerDp(300.0, width, height, coveredStartDp = 700f) > zoom(300.0))
  }
}
