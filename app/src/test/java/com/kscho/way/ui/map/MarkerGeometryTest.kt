package com.kscho.way.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 피그마 확정 마커(이름표가 핀·점 바로 위 가운데) — 좌표가 놓이는 점과 이름표 자리. */
class MarkerGeometryTest {
  @Test
  fun pin_tipIsTheAnchor_andTheTagSitsAboveItCentered() {
    val g = MarkerGeometry.of(MarkerGeometry.Shape.PIN, tagWidth = 76f)
    // 좌표 = 핀 끝(그림 안 54.97)
    assertEquals(g.graphicTop + 54.97f, g.anchorY, 0.01f)
    // 이름표 아래 끝과 핀의 보이는 위 끝(그림 안 10) 사이 10
    assertEquals(MarkerGeometry.GAP, g.graphicTop + 10f - (g.tagTop + MarkerGeometry.TAG_HEIGHT), 0.01f)
    // 가로 가운데
    assertEquals(g.width / 2, g.tagLeft + 76f / 2, 0.01f)
    assertEquals(g.width / 2, g.graphicLeft + MarkerGeometry.PIN_WIDTH / 2, 0.01f)
  }

  @Test
  fun dot_centerIsTheAnchor() {
    val g = MarkerGeometry.of(MarkerGeometry.Shape.DOT, tagWidth = 59f)
    assertEquals(g.graphicTop + 28f, g.anchorY, 0.01f)
    // 점의 보이는 위 끝 = 가운데 - 12
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
