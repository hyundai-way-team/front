package com.kscho.way.ui.drive

import org.junit.Assert.assertEquals
import org.junit.Test

/** D-08 · D-11 진행 고리(피그마 Loading: 지름 96, 띠 9.6) — 파란 호가 회색 고리 띠 안에서 돈다. */
class RingGeometryTest {
  @Test
  fun bandIsTenPercentOfTheDiameter() {
    assertEquals(9.6f, RingGeometry.of(96f).stroke, 0.001f)
  }

  @Test
  fun arcRunsOnTheMiddleOfTheBand_soItsOuterEdgeIsTheRingsOuterEdge() {
    val ring = RingGeometry.of(96f)
    // 띠 가운데 = 바깥 48 과 안쪽 38.4 사이
    assertEquals(43.2f, ring.radius, 0.001f)
    assertEquals(48f, ring.radius + ring.stroke / 2, 0.001f)
    assertEquals(38.4f, ring.radius - ring.stroke / 2, 0.001f)
  }
}
