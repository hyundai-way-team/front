package com.kscho.way.ui.map

import kotlin.math.max

/**
 * 피그마 확정 마커의 배치(단위 dp) — 이름표(Tag/Location 464:2652, View=Navigation)가 핀(Icons/Marker 442:2529)이나
 * 점(Location Dot 477:2671) 바로 위 가운데에 붙는다. 좌표가 놓이는 점([anchorX], [anchorY])은 핀 끝 · 점 가운데이고,
 * 지도가 그림 가운데를 좌표에 맞추면 되도록 그 점이 그림 한가운데가 되게 아래를 비워 둔다.
 */
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
    /** 이름표 높이: 글자 16 × 줄높이 1.5 + 위아래 여백 2 × 2 + 테두리 1 × 2. */
    const val TAG_HEIGHT = 30f
    /** 이름표 아래 끝과 핀·점의 보이는 위 끝 사이. */
    const val GAP = 10f
    /** 이름표 그림자가 잘리지 않게 둘레에 두는 여백. */
    const val SHADOW_PAD = 6f

    /** 핀 그림(글로 포함, 피그마 SVG 그대로): 보이는 위 끝 10, 끝점 54.97. */
    const val PIN_WIDTH = 53f
    const val PIN_HEIGHT = 64.9736f
    private const val PIN_VISIBLE_TOP = 10f
    private const val PIN_TIP = 54.9736f

    /** 점 그림(그림자 포함): 가운데 28, 흰 테두리까지 반지름 12. */
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
