package com.kscho.way.domain.pickup

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
