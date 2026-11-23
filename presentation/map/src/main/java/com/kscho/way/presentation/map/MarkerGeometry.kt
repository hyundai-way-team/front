package com.kscho.way.presentation.map

import kotlin.math.max

internal data class MarkerGeometry(
  val width: Float,
  val height: Float,
  val tagLeft: Float,
  val tagTop: Float,
  val graphicLeft: Float,
  val graphicTop: Float,
  val anchorX: Float,
  val anchorY: Float,
) {
  enum class Shape {
    PIN,
    DOT,
  }

  companion object {
    const val TAG_HEIGHT = 30f
    const val GAP = 10f
    const val SHADOW_PAD = 6f

    const val PIN_WIDTH = 53f
    const val PIN_HEIGHT = 64.9736f
    private const val PIN_VISIBLE_TOP = 10f
    private const val PIN_TIP = 54.9736f

    const val DOT_SIZE = 56f
    private const val DOT_CENTER = 28f
    private const val DOT_RADIUS = 12f

    fun of(shape: Shape, tagWidth: Float): MarkerGeometry {
      val graphicWidth = if (shape == Shape.PIN) PIN_WIDTH else DOT_SIZE
      val visibleTop = if (shape == Shape.PIN) PIN_VISIBLE_TOP else DOT_CENTER - DOT_RADIUS
      val anchorInGraphic = if (shape == Shape.PIN) PIN_TIP else DOT_CENTER
      val tagTop = SHADOW_PAD
      val graphicTop = tagTop + TAG_HEIGHT + GAP - visibleTop
      val anchorY = graphicTop + anchorInGraphic
      val width = max(graphicWidth, tagWidth + 2 * SHADOW_PAD)
      return MarkerGeometry(
        width = width,
        height = 2 * anchorY,
        tagLeft = (width - tagWidth) / 2,
        tagTop = tagTop,
        graphicLeft = (width - graphicWidth) / 2,
        graphicTop = graphicTop,
        anchorX = width / 2,
        anchorY = anchorY,
      )
    }
  }
}
