package com.kscho.way.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val latitude: Double, val longitude: Double)

/** 짧은 거리(도심 픽업 범위) 계산. 거리는 미터. */
object Geo {
  private const val EARTH_RADIUS_M = 6_371_000.0

  fun distanceMeters(a: LatLng, b: LatLng): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
  }

  fun lerp(a: LatLng, b: LatLng, t: Double): LatLng =
    LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)

  fun pathLengthMeters(path: List<LatLng>): Double = path.zipWithNext { a, b -> distanceMeters(a, b) }.sum()

  /** 경로 시작점에서 [meters] 만큼 간 지점. 경로 길이를 넘으면 끝점. */
  fun pointAlong(path: List<LatLng>, meters: Double): LatLng {
    require(path.isNotEmpty()) { "빈 경로" }
    var left = meters.coerceAtLeast(0.0)
    for ((a, b) in path.zipWithNext()) {
      val d = distanceMeters(a, b)
      if (left <= d) return if (d == 0.0) a else lerp(a, b, left / d)
      left -= d
    }
    return path.last()
  }

  /** 점에서 경로(선분들)까지 가장 짧은 거리. */
  fun distanceToPathMeters(p: LatLng, path: List<LatLng>): Double =
    when (path.size) {
      0 -> Double.POSITIVE_INFINITY
      1 -> distanceMeters(p, path[0])
      else -> path.zipWithNext { a, b -> distanceToSegmentMeters(p, a, b) }.min()
    }

  /** [origin] 기준 동쪽(x)·북쪽(y) 미터. 짧은 거리라 평면으로 본다. */
  fun offsetMeters(origin: LatLng, p: LatLng): Pair<Double, Double> {
    val x = Math.toRadians(p.longitude - origin.longitude) * cos(Math.toRadians(origin.latitude)) * EARTH_RADIUS_M
    val y = Math.toRadians(p.latitude - origin.latitude) * EARTH_RADIUS_M
    return x to y
  }

  /** [center] 둘레 [radiusMeters] 원을 [steps] 개 점으로 — 닫힌 고리(끝 점 = 첫 점). 지도에 점 구간(정류장 10m)을 그릴 때. */
  fun circle(center: LatLng, radiusMeters: Double, steps: Int = 24): List<LatLng> =
    (0..steps).map { i ->
      val a = 2 * Math.PI * (i % steps) / steps
      offset(center, east = radiusMeters * kotlin.math.cos(a), north = radiusMeters * kotlin.math.sin(a))
    }

  /** [origin] 에서 동쪽 [east]m · 북쪽 [north]m 떨어진 곳. */
  fun offset(origin: LatLng, east: Double, north: Double): LatLng =
    LatLng(
      origin.latitude + Math.toDegrees(north / EARTH_RADIUS_M),
      origin.longitude + Math.toDegrees(east / (EARTH_RADIUS_M * cos(Math.toRadians(origin.latitude)))),
    )

  private fun distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
    val (ax, ay) = offsetMeters(p, a)
    val (bx, by) = offsetMeters(p, b)
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy).div(len2).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy)
  }
}
