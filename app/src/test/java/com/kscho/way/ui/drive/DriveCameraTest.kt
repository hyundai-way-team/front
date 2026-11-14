package com.kscho.way.ui.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-06·D-07 주행 지도 확대 — 카드의 남은 거리가 가까워지면 한 번만 확대한다. */
class DriveCameraTest {
  @Test
  fun far_keepsTheWideDrivingZoom() {
    assertFalse(DriveCamera.zoomedIn(wasZoomedIn = false, remainingMeters = 3_000))
    assertFalse(DriveCamera.zoomedIn(wasZoomedIn = false, remainingMeters = DriveCamera.ZOOM_IN_REMAINING_METERS + 1))
    assertEquals(DriveCamera.FAR_METERS_PER_DP, DriveCamera.metersPerDp(zoomedIn = false))
  }

  @Test
  fun remainingDistanceReachesTheLine_zoomsInOnce() {
    assertTrue(DriveCamera.zoomedIn(wasZoomedIn = false, remainingMeters = DriveCamera.ZOOM_IN_REMAINING_METERS))
    assertEquals(DriveCamera.CLOSE_METERS_PER_DP, DriveCamera.metersPerDp(zoomedIn = true))
    assertTrue("한 번 확대하면 1dp 가 덮는 거리가 줄어야 한다", DriveCamera.CLOSE_METERS_PER_DP < DriveCamera.FAR_METERS_PER_DP)
  }

  @Test
  fun closerStill_doesNotZoomAgain() {
    // 점점 확대하지 않는다 — 남은 거리가 더 줄어도 같은 확대
    val at400 = DriveCamera.metersPerDp(DriveCamera.zoomedIn(wasZoomedIn = false, remainingMeters = 400))
    val at100 = DriveCamera.metersPerDp(DriveCamera.zoomedIn(wasZoomedIn = true, remainingMeters = 100))
    assertEquals(at400, at100)
  }

  @Test
  fun onceZoomedIn_staysEvenIfTheRouteGetsLonger() {
    // 길을 다시 잡아 남은 거리가 늘어도 다시 축소하지 않는다(한 번만)
    assertTrue(DriveCamera.zoomedIn(wasZoomedIn = true, remainingMeters = 1_200))
  }

  @Test
  fun unknownRemaining_keepsWhatItWas() {
    assertFalse(DriveCamera.zoomedIn(wasZoomedIn = false, remainingMeters = null))
    assertTrue(DriveCamera.zoomedIn(wasZoomedIn = true, remainingMeters = null))
  }
}
