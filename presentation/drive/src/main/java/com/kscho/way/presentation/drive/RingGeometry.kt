package com.kscho.way.presentation.drive

internal data class RingGeometry(val stroke: Float, val radius: Float) {
  companion object {
    private const val BAND = 0.1f

    fun of(diameter: Float): RingGeometry {
      val stroke = diameter * BAND
      return RingGeometry(stroke = stroke, radius = diameter / 2 - stroke / 2)
    }
  }
}
