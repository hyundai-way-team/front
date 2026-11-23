package com.kscho.way.server.zones

import com.kscho.way.data.dto.LatLngDto
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 지정 주정차 금지구역에 나오는 도로들의 선(OpenStreetMap, © OpenStreetMap contributors · ODbL).
 * tools/data/build_no_parking_zones.py 가 같은 이름 길 조각을 이어 붙여 둔다.
 *
 * 화면 안에 지나는 도로 이름을 골라(카카오를 부를 금지구역 줄이기) 끝점 둘을 도로 위에 맞추고 그 사이를 도로를 따라 자른다.
 * 거리는 질문 지점 둘레를 평면으로 펴서 m 로 잰다(도심 몇 km 안이라 충분).
 */
class RoadIndex(private val roads: List<Road>) {
  class Road(val name: String, val kind: String, val lat: DoubleArray, val lng: DoubleArray) {
    init {
      require(lat.size == lng.size && lat.size >= 2) { "도로 선은 점 두 개 이상" }
    }

    /** 가운데 선에서 길가(인도 끝)까지 대략 — 도로 종류로 어림한다(차로 수 데이터가 들쭉날쭉해서). */
    val halfWidthMeters: Double
      get() =
        when (kind) {
          "trunk" -> 15.0
          "primary" -> 12.0
          "secondary" -> 9.0
          "tertiary" -> 7.0
          else -> 6.0
        }
  }

  /** 도로 위 한 점: 선 [road] 의 시작에서 [along] m, 원래 점에서 [distance] m 떨어짐. */
  data class Snap(val road: Road, val along: Double, val distance: Double, val point: LatLngDto)

  private val grid: Map<Long, IntArray>

  init {
    val cells = HashMap<Long, MutableSet<Int>>()
    roads.forEachIndexed { i, r ->
      for (k in 0 until r.lat.size - 1) {
        for (y in cell(min(r.lat[k], r.lat[k + 1]))..cell(max(r.lat[k], r.lat[k + 1]))) {
          for (x in cell(min(r.lng[k], r.lng[k + 1]))..cell(max(r.lng[k], r.lng[k + 1]))) cells.getOrPut(key(y, x)) { HashSet() } += i
        }
      }
    }
    grid = cells.mapValues { it.value.toIntArray() }
  }

  val size: Int
    get() = roads.size

  /** ([lat],[lng]) 둘레 [radiusMeters] 안을 지나는 도로 선과 그 최단 거리(m), 가까운 순. */
  fun near(lat: Double, lng: Double, radiusMeters: Double): List<Pair<Road, Double>> {
    val dLat = radiusMeters / M_PER_DEG
    val dLng = radiusMeters / (M_PER_DEG * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
    val ids = HashSet<Int>()
    for (y in cell(lat - dLat)..cell(lat + dLat)) for (x in cell(lng - dLng)..cell(lng + dLng)) grid[key(y, x)]?.let { a -> a.forEach { ids += it } }
    return ids.map { roads[it] }.map { it to snap(it, lat, lng).distance }.filter { it.second <= radiusMeters }.sortedBy { it.second }
  }

  companion object {
    private const val CELL = 0.01
    private const val M_PER_DEG = 111_320.0
    private const val RESOURCE = "/data/no_parking_roads_capital.txt.gz"

    private fun cell(v: Double) = floor(v / CELL).toInt()

    private fun key(y: Int, x: Int) = (y.toLong() shl 32) or (x.toLong() and 0xffffffffL)

    /** 점을 선 위에 내린다(가장 가까운 자리). */
    fun snap(road: Road, lat: Double, lng: Double): Snap {
      // along 이 length·cut 과 같은 잣대가 되도록 선의 첫 점 위도로 편다
      val kx = M_PER_DEG * cos(Math.toRadians(road.lat[0]))
      var best = Double.MAX_VALUE
      var bestAlong = 0.0
      var bestLat = road.lat[0]
      var bestLng = road.lng[0]
      var walked = 0.0
      for (k in 0 until road.lat.size - 1) {
        val ax = (road.lng[k] - lng) * kx
        val ay = (road.lat[k] - lat) * M_PER_DEG
        val bx = (road.lng[k + 1] - lng) * kx
        val by = (road.lat[k + 1] - lat) * M_PER_DEG
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val d = hypot(ax + t * dx, ay + t * dy)
        val segLen = kotlin.math.sqrt(len2)
        if (d < best) {
          best = d
          bestAlong = walked + t * segLen
          bestLat = road.lat[k] + t * (road.lat[k + 1] - road.lat[k])
          bestLng = road.lng[k] + t * (road.lng[k + 1] - road.lng[k])
        }
        walked += segLen
      }
      return Snap(road, bestAlong, best, LatLngDto(bestLat, bestLng))
    }

    /** 선 길이(m). */
    fun length(road: Road): Double {
      val kx = M_PER_DEG * cos(Math.toRadians(road.lat[0]))
      var sum = 0.0
      for (k in 0 until road.lat.size - 1) sum += hypot((road.lng[k + 1] - road.lng[k]) * kx, (road.lat[k + 1] - road.lat[k]) * M_PER_DEG)
      return sum
    }

    /** 선의 [from]~[to] m 부분(순서 상관없음, 선 밖은 잘라 맞춘다). */
    fun cut(road: Road, from: Double, to: Double): List<LatLngDto> {
      val total = length(road)
      val a = min(from, to).coerceIn(0.0, total)
      val b = max(from, to).coerceIn(0.0, total)
      val kx = M_PER_DEG * cos(Math.toRadians(road.lat[0]))
      val out = ArrayList<LatLngDto>()
      var walked = 0.0
      for (k in 0 until road.lat.size - 1) {
        val segLen = hypot((road.lng[k + 1] - road.lng[k]) * kx, (road.lat[k + 1] - road.lat[k]) * M_PER_DEG)
        val s = walked
        val e = walked + segLen
        fun at(m: Double): LatLngDto {
          val t = if (segLen == 0.0) 0.0 else ((m - s) / segLen).coerceIn(0.0, 1.0)
          return LatLngDto(road.lat[k] + t * (road.lat[k + 1] - road.lat[k]), road.lng[k] + t * (road.lng[k + 1] - road.lng[k]))
        }
        if (e >= a && s <= b) {
          if (out.isEmpty()) out += at(max(a, s))
          if (e <= b) out += at(e) else out += at(b)
        }
        walked = e
        if (walked > b) break
      }
      return out.distinct()
    }

    /** '이름\t종류\t위도,경도 위도,경도 …' 줄들('#' 줄은 설명). */
    fun parse(input: InputStream): RoadIndex =
      RoadIndex(
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
          lines
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
              val p = line.split('\t')
              if (p.size < 3) return@mapNotNull null
              val pts = p[2].split(' ').mapNotNull { xy -> xy.split(',').takeIf { it.size == 2 }?.let { (a, b) -> a.toDoubleOrNull()?.let { la -> b.toDoubleOrNull()?.let { lo -> la to lo } } } }
              if (pts.size < 2) return@mapNotNull null
              Road(p[0], p[1], DoubleArray(pts.size) { pts[it].first }, DoubleArray(pts.size) { pts[it].second })
            }
            .toList()
        }
      )

    fun bundled(): RoadIndex {
      val stream = RoadIndex::class.java.getResourceAsStream(RESOURCE) ?: error("도로 선 데이터가 없어요: $RESOURCE")
      return GZIPInputStream(stream).use { parse(it) }
    }
  }
}
