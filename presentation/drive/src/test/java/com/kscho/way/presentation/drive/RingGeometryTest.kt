package com.kscho.way.presentation.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class RingGeometryTest {
  @Test
  fun bandIsTenPercentOfTheDiameter() {
    assertEquals(9.6f, RingGeometry.of(96f).stroke, 0.001f)
  }

  @Test
  fun arcRunsOnTheMiddleOfTheBand_soItsOuterEdgeIsTheRingsOuterEdge() {
    val ring = RingGeometry.of(96f)
    assertEquals(43.2f, ring.radius, 0.001f)
    assertEquals(48f, ring.radius + ring.stroke / 2, 0.001f)
    assertEquals(38.4f, ring.radius - ring.stroke / 2, 0.001f)
  }
}
