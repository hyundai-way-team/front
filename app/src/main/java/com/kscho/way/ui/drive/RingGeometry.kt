package com.kscho.way.ui.drive

/**
 * 진행 고리 치수(피그마 Loading: 지름 96 · 띠 9.6). 바탕 고리와 남은 시간 호가 같은 원 — 띠 가운데([radius]) — 위에 같은 두께로 그려져야
 * 호가 고리 띠 안에서 돈다.
 */
internal data class RingGeometry(val stroke: Float, val radius: Float) {
  companion object {
    private const val BAND = 0.1f

    fun of(diameter: Float): RingGeometry {
      val stroke = diameter * BAND
      return RingGeometry(stroke = stroke, radius = diameter / 2 - stroke / 2)
    }
  }
}
