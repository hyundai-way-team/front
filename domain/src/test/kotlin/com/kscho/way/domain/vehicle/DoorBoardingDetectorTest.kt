package com.kscho.way.domain.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorBoardingDetectorTest {
  @Test
  fun closingAnOpenedDoor_isBoarding() {
    val detector = DoorBoardingDetector()
    assertFalse(detector.onDoor("ROW_1_RIGHT", isOpen = true))
    assertTrue(detector.onDoor("ROW_1_RIGHT", isOpen = false))
  }

  @Test
  fun closedReportWithoutOpening_isNotBoarding() {
    val detector = DoorBoardingDetector()
    // 구독 직후 현재 상태(닫힘)가 먼저 온다
    assertFalse(detector.onDoor("ROW_2_RIGHT", isOpen = false))
    assertFalse(detector.onDoor("ROW_2_RIGHT", isOpen = false))
  }

  @Test
  fun doorsAreTrackedSeparately() {
    val detector = DoorBoardingDetector()
    detector.onDoor("ROW_1_RIGHT", isOpen = true)
    assertFalse(detector.onDoor("ROW_2_LEFT", isOpen = false))
    assertTrue(detector.onDoor("ROW_1_RIGHT", isOpen = false))
  }
}
