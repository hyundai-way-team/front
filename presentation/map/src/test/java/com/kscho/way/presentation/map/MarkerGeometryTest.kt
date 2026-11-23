package com.kscho.way.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerGeometryTest {
  @Test
  fun pin_tipIsTheAnchor_andTheTagSitsAboveItCentered() {
    val g = MarkerGeometry.of(MarkerGeometry.Shape.PIN, tagWidth = 76f)
    assertEquals(g.graphicTop + 54.97f, g.anchorY, 0.01f)
    assertEquals(MarkerGeometry.GAP, g.graphicTop + 10f - (g.tagTop + MarkerGeometry.TAG_HEIGHT), 0.01f)
    assertEquals(g.width / 2, g.tagLeft + 76f / 2, 0.01f)
    assertEquals(g.width / 2, g.graphicLeft + MarkerGeometry.PIN_WIDTH / 2, 0.01f)
  }

  @Test
  fun dot_centerIsTheAnchor() {
    val g = MarkerGeometry.of(MarkerGeometry.Shape.DOT, tagWidth = 59f)
    assertEquals(g.graphicTop + 28f, g.anchorY, 0.01f)
    assertEquals(MarkerGeometry.GAP, g.anchorY - 12f - (g.tagTop + MarkerGeometry.TAG_HEIGHT), 0.01f)
  }

  @Test
  fun anchorIsTheCenterOfTheImage_soTheMapCanCenterIt() {
    for (shape in MarkerGeometry.Shape.entries) {
      val g = MarkerGeometry.of(shape, tagWidth = 120f)
      assertEquals(g.width / 2, g.anchorX, 0.01f)
      assertEquals(g.height / 2, g.anchorY, 0.01f)
    }
  }

  @Test
  fun wideTag_widensTheImage_narrowTag_keepsTheGraphicWidth() {
    assertTrue(MarkerGeometry.of(MarkerGeometry.Shape.PIN, tagWidth = 200f).width >= 200f + 2 * MarkerGeometry.SHADOW_PAD)
    assertTrue(MarkerGeometry.of(MarkerGeometry.Shape.DOT, tagWidth = 10f).width >= MarkerGeometry.DOT_SIZE)
  }
}
