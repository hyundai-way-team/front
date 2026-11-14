package com.kscho.way.server.walk

import com.kscho.way.data.remote.LatLngDto
import kotlin.math.cos
import kotlin.math.sqrt

/** 경로 선 다듬기(차 길) — 도심 짧은 거리라 평면(m)으로 본다. */
object RoutePaths {
  /** [here] 에서 시작해 [path] 의 남은 부분 — 차가 올라탄 구간(가장 가까운 선분) 다음 꼭짓점부터. */
  fun remainingFrom(path: List<LatLngDto>, here: LatLngDto): List<LatLngDto> {
    if (path.size < 2) return listOf(here) + path
    val segment = (0 until path.size - 1).minBy { distanceToSegment(here, path[it], path[it + 1]) }
    return listOf(here) + path.drop(segment + 1)
  }

  /** [here] 가 [path] 에서 떨어진 거리(m). */
  fun distanceToPath(here: LatLngDto, path: List<LatLngDto>): Double =
    when (path.size) {
      0 -> Double.POSITIVE_INFINITY
      1 -> OsmWalkRoutes.distanceMeters(here, path[0])
      else -> (0 until path.size - 1).minOf { distanceToSegment(here, path[it], path[it + 1]) }
    }

  /** 더글러스-포커 — [toleranceMeters] 보다 덜 휘는 점은 뺀다(꺾이는 곳은 남는다). 웹으로 자주 보내는 선이라 점을 줄인다. */
  fun simplify(path: List<LatLngDto>, toleranceMeters: Double): List<LatLngDto> {
    if (path.size < 3) return path
    val keep = BooleanArray(path.size).also {
      it[0] = true
      it[path.lastIndex] = true
    }
    val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to path.lastIndex) }
    while (stack.isNotEmpty()) {
      val (from, to) = stack.removeLast()
      if (to - from < 2) continue
      var far = -1
      var farDistance = 0.0
      for (i in from + 1 until to) {
        val d = distanceToSegment(path[i], path[from], path[to])
        if (d > farDistance) {
          farDistance = d
          far = i
        }
      }
      if (far >= 0 && farDistance > toleranceMeters) {
        keep[far] = true
        stack.add(from to far)
        stack.add(far to to)
      }
    }
    return path.filterIndexed { i, _ -> keep[i] }
  }

  private fun distanceToSegment(p: LatLngDto, a: LatLngDto, b: LatLngDto): Double {
    // a 를 원점으로 한 평면 좌표(m)
    val kx = 111_195.0 * cos(Math.toRadians(a.lat))
    val ky = 111_195.0
    val px = (p.lng - a.lng) * kx
    val py = (p.lat - a.lat) * ky
    val bx = (b.lng - a.lng) * kx
    val by = (b.lat - a.lat) * ky
    val len2 = bx * bx + by * by
    val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
    val dx = px - t * bx
    val dy = py - t * by
    return sqrt(dx * dx + dy * dy)
  }
}
