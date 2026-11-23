package com.kscho.way.data.place

import com.kscho.way.domain.model.geo.LatLng
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 오프라인 장소 색인 — 앱에 넣은 OpenStreetMap 지도(way.pmtiles)에서 뽑은 장소(POI) · 이름 있는 도로선 · 시·구·동 이름 점.
 * 외부 지도·검색 API 를 부르지 않는다(나중에 Pleos SDK 로 바꿀 자리).
 *
 * 파일은 `tools/map/build_places_index.py` 가 만들고 [PlaceIndexReader] 가 읽는다. 검사에서는 [of] 로 작게 만든다.
 * 좌표는 1e-6 도 정수로 들고, 가까운 것 찾기용 칸 색인(약 110 m)을 만들 때 한 번 짓는다. 만든 뒤에는 바뀌지 않아 여러 스레드에서 읽어도 된다.
 */
class PlaceIndex internal constructor(
  // 시·구·동 이름 점
  internal val areaName: Array<String>,
  internal val areaLevel: Array<AreaLevel>,
  /** 윗주소. 연남동 → "서울 마포구", 마포구 → "서울", 서울특별시 → "" */
  internal val areaParent: Array<String>,
  internal val areaLat: IntArray,
  internal val areaLng: IntArray,
  // 장소
  internal val poiName: Array<String>,
  internal val poiKind: IntArray,
  internal val kindClass: Array<String>,
  internal val kindSubclass: Array<String>,
  /** 장소가 있는 동(없으면 구) — [areaName] 번호, 모르면 -1 */
  internal val poiArea: IntArray,
  /** [FLAG_NOT_STREET] … */
  internal val poiFlags: ByteArray,
  internal val poiLat: IntArray,
  internal val poiLng: IntArray,
  // 도로선: i 번째 도로의 점은 roadStart[i] until roadStart[i + 1]
  internal val roadName: Array<String>,
  internal val roadClass: Array<RoadClass>,
  internal val roadStart: IntArray,
  internal val pointLat: IntArray,
  internal val pointLng: IntArray,
) {
  init {
    require(areaLevel.size == areaName.size && areaParent.size == areaName.size && areaLat.size == areaName.size && areaLng.size == areaName.size)
    require(poiKind.size == poiName.size && poiArea.size == poiName.size && poiFlags.size == poiName.size)
    require(poiLat.size == poiName.size && poiLng.size == poiName.size && kindSubclass.size == kindClass.size)
    require(roadClass.size == roadName.size && roadStart.size == roadName.size + 1 && pointLat.size == pointLng.size)
    require(roadStart.last() == pointLat.size)
  }

  val areaCount: Int get() = areaName.size
  val poiCount: Int get() = poiName.size
  val roadCount: Int get() = roadName.size

  // ── 검색용 글자(공백·기호 빼고 소문자) — 대부분 이름 그대로라 같은 문자열을 쓴다

  internal val areaKey: Array<String> = Array(areaName.size) { PlaceText.keyOrSame(areaName[it]) }
  internal val areaParentKey: Array<String> = Array(areaName.size) { PlaceText.keyOrSame(areaParent[it]) }
  internal val areaAddressKey: Array<String> = Array(areaName.size) { areaParentKey[it] + areaKey[it] }
  internal val poiKey: Array<String> = Array(poiName.size) { PlaceText.keyOrSame(poiName[it]) }
  internal val kindOf: Array<PoiKind> = Array(kindClass.size) { PoiKind.of(kindClass[it], kindSubclass[it]) }

  // ── 칸 색인

  private val bounds = Bounds().apply {
    for (i in areaLat.indices) add(areaLat[i], areaLng[i])
    for (i in poiLat.indices) add(poiLat[i], poiLng[i])
    for (i in pointLat.indices) add(pointLat[i], pointLng[i])
  }

  private val poiGrid =
    CellGrid.build(bounds, poiName.size) { i, box ->
      box[0] = poiLat[i]
      box[1] = poiLng[i]
      box[2] = poiLat[i]
      box[3] = poiLng[i]
      true
    }

  /** 도로마다 마지막 점 — 다음 도로 첫 점과 잇지 않는다. */
  private val roadEnds = BooleanArray(pointLat.size).also { ends -> for (r in roadName.indices) if (roadStart[r + 1] > 0) ends[roadStart[r + 1] - 1] = true }

  private fun isRoadEnd(point: Int) = roadEnds[point]

  /** 도로 조각(점 i → i+1) 색인. 항목 번호 = 앞 점 번호. */
  private val segmentGrid =
    CellGrid.build(bounds, pointLat.size) { i, box ->
      if (i + 1 >= pointLat.size || isRoadEnd(i)) return@build false
      box[0] = minOf(pointLat[i], pointLat[i + 1])
      box[1] = minOf(pointLng[i], pointLng[i + 1])
      box[2] = maxOf(pointLat[i], pointLat[i + 1])
      box[3] = maxOf(pointLng[i], pointLng[i + 1])
      true
    }

  // ── 읽기

  fun area(i: Int): Area = Area(areaName[i], areaLevel[i], areaParent[i], latLng(areaLat[i], areaLng[i]))

  fun poi(i: Int): Poi =
    Poi(poiName[i], kindClass[poiKind[i]], kindSubclass[poiKind[i]], latLng(poiLat[i], poiLng[i]), poiArea[i], (poiFlags[i].toInt() and FLAG_NOT_STREET) == 0)

  fun road(i: Int): Road = Road(roadName[i], roadClass[i], (roadStart[i] until roadStart[i + 1]).map { latLng(pointLat[it], pointLng[it]) })

  internal fun poiKindOf(i: Int): PoiKind = kindOf[poiKind[i]]

  internal fun poiStreetLevel(i: Int): Boolean = (poiFlags[i].toInt() and FLAG_NOT_STREET) == 0

  /** 장소 i 의 주소("서울 마포구 연남동"). 동을 모르면 가장 가까운 동 이름 점의 주소. */
  internal fun poiAddress(i: Int): String {
    val area = poiArea[i].takeIf { it >= 0 } ?: nearestArea(poiLat[i], poiLng[i], AreaLevel.QUARTER, maxMeters = 3_000.0) ?: return ""
    return fullAddress(area)
  }

  internal fun fullAddress(area: Int): String = if (areaParent[area].isEmpty()) areaName[area] else "${areaParent[area]} ${areaName[area]}"

  internal fun nearestArea(lat: Int, lng: Int, level: AreaLevel, maxMeters: Double): Int? {
    val cosLat = cosOf(lat)
    var best = -1
    var bestMeters = maxMeters
    for (i in areaName.indices) {
      if (areaLevel[i] != level) continue
      val d = meters(lat, lng, areaLat[i], areaLng[i], cosLat)
      if (d <= bestMeters) {
        bestMeters = d
        best = i
      }
    }
    return best.takeIf { it >= 0 }
  }

  // ── 가까운 것 찾기

  internal class PoiHit(val poi: Int, val meters: Double)

  /** [center] 에서 [radiusMeters] 안 장소. 가까운 순. */
  internal fun poisNear(center: LatLng, radiusMeters: Double): List<PoiHit> {
    val lat = e6(center.latitude)
    val lng = e6(center.longitude)
    val cosLat = cosOf(lat)
    val hits = ArrayList<PoiHit>()
    poiGrid.forEachNear(lat, lng, latSpan(radiusMeters), lngSpan(radiusMeters, cosLat)) { i ->
      val d = meters(lat, lng, poiLat[i], poiLng[i], cosLat)
      if (d <= radiusMeters) hits += PoiHit(i, d)
    }
    hits.sortBy { it.meters }
    return hits
  }

  /**
   * 도로 하나에서 [center] 에 가장 가까운 곳.
   * [east]·[north] = 점에서 그곳까지(미터), [normalEast]·[normalNorth] = 도로에서 점 쪽을 가리키는 단위 법선.
   */
  internal class RoadHit(
    val road: Int,
    val meters: Double,
    val east: Double,
    val north: Double,
    val normalEast: Double,
    val normalNorth: Double,
  )

  /** [center] 에서 [radiusMeters] 안 도로(도로마다 가장 가까운 곳 하나). 가까운 순. */
  internal fun roadsNear(center: LatLng, radiusMeters: Double): List<RoadHit> {
    val lat = e6(center.latitude)
    val lng = e6(center.longitude)
    val cosLat = cosOf(lat)
    val best = HashMap<Int, RoadHit>()
    segmentGrid.forEachNear(lat, lng, latSpan(radiusMeters), lngSpan(radiusMeters, cosLat)) { i ->
      // 점을 원점으로 한 평면(미터)
      val ax = (pointLng[i] - lng) * M_PER_E6 * cosLat
      val ay = (pointLat[i] - lat) * M_PER_E6
      val bx = (pointLng[i + 1] - lng) * M_PER_E6 * cosLat
      val by = (pointLat[i + 1] - lat) * M_PER_E6
      val dx = bx - ax
      val dy = by - ay
      val len2 = dx * dx + dy * dy
      val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
      val nx = ax + t * dx
      val ny = ay + t * dy
      val d = sqrt(nx * nx + ny * ny)
      if (d > radiusMeters) return@forEachNear
      val road = roadOf(i)
      val old = best[road]
      if (old != null && old.meters <= d) return@forEachNear
      // 법선: 조각 방향의 왼쪽/오른쪽 중 점이 있는 쪽
      val cross = dx * (-ay) - dy * (-ax)
      val len = sqrt(len2)
      val (ex, ey) =
        when {
          len == 0.0 -> if (d == 0.0) 0.0 to 0.0 else -nx / d to -ny / d
          cross >= 0 -> -dy / len to dx / len
          else -> dy / len to -dx / len
        }
      best[road] = RoadHit(road, d, nx, ny, ex, ey)
    }
    return best.values.sortedBy { it.meters }
  }

  /** [from]→[to] 곧은 선이 도로 [road] 를 가로지르나(길 건너편인지). */
  internal fun crossesRoad(from: LatLng, to: LatLng, road: Int): Boolean {
    val py = e6(from.latitude).toLong()
    val px = e6(from.longitude).toLong()
    val qy = e6(to.latitude).toLong()
    val qx = e6(to.longitude).toLong()
    for (i in roadStart[road] until roadStart[road + 1] - 1) {
      val ay = pointLat[i].toLong()
      val ax = pointLng[i].toLong()
      val by = pointLat[i + 1].toLong()
      val bx = pointLng[i + 1].toLong()
      val d1 = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
      val d2 = (bx - ax) * (qy - ay) - (by - ay) * (qx - ax)
      if (d1 == 0L || d2 == 0L || (d1 > 0) == (d2 > 0)) continue
      val d3 = (qx - px) * (ay - py) - (qy - py) * (ax - px)
      val d4 = (qx - px) * (by - py) - (qy - py) * (bx - px)
      if (d3 != 0L && d4 != 0L && (d3 > 0) != (d4 > 0)) return true
    }
    return false
  }

  private fun roadOf(point: Int): Int {
    // roadStart 는 오름차순 — point 가 든 도로
    var lo = 0
    var hi = roadName.size - 1
    while (lo < hi) {
      val mid = (lo + hi + 1) ushr 1
      if (roadStart[mid] <= point) lo = mid else hi = mid - 1
    }
    return lo
  }

  // ── 검사·바깥에서 만들 때 쓰는 모양

  /** 시·구·동 이름 점. [parentAddress] 예) 연남동 → "서울 마포구" */
  data class Area(val name: String, val level: AreaLevel, val parentAddress: String, val location: LatLng) {
    val fullAddress: String get() = if (parentAddress.isEmpty()) name else "$parentAddress $name"
  }

  /**
   * 장소. [name] 은 화면에 그대로 쓰는 이름(역은 "신촌역", 버스정류장은 "○○ 버스정류장").
   * [category]·[subclass] 는 OpenMapTiles poi 의 class·subclass. [area] 는 [Area] 번호(-1 = 모름).
   * [streetLevel] false = 실내·지하·2층 이상 — 핀 설명의 "○○ 앞" 에 안 쓴다.
   */
  data class Poi(
    val name: String,
    val category: String,
    val subclass: String,
    val location: LatLng,
    val area: Int = -1,
    val streetLevel: Boolean = true,
  )

  data class Road(val name: String, val roadClass: RoadClass, val path: List<LatLng>)

  companion object {
    /** 장소 표시: 길에서 안 보이는 곳 */
    const val FLAG_NOT_STREET = 1

    /** 작은 색인을 바로 만든다(검사·미리보기). */
    fun of(areas: List<Area> = emptyList(), pois: List<Poi> = emptyList(), roads: List<Road> = emptyList()): PlaceIndex {
      val kinds = LinkedHashMap<Pair<String, String>, Int>()
      for (p in pois) kinds.getOrPut(p.category to p.subclass) { kinds.size }
      val starts = IntArray(roads.size + 1)
      for ((i, r) in roads.withIndex()) {
        require(r.path.size >= 2) { "도로 '${r.name}' 은 점이 둘 이상이어야 한다" }
        starts[i + 1] = starts[i] + r.path.size
      }
      val points = roads.flatMap { it.path }
      return PlaceIndex(
        areaName = areas.map { it.name }.toTypedArray(),
        areaLevel = areas.map { it.level }.toTypedArray(),
        areaParent = areas.map { it.parentAddress }.toTypedArray(),
        areaLat = areas.map { e6(it.location.latitude) }.toIntArray(),
        areaLng = areas.map { e6(it.location.longitude) }.toIntArray(),
        poiName = pois.map { it.name }.toTypedArray(),
        poiKind = pois.map { kinds.getValue(it.category to it.subclass) }.toIntArray(),
        kindClass = kinds.keys.map { it.first }.toTypedArray(),
        kindSubclass = kinds.keys.map { it.second }.toTypedArray(),
        poiArea = pois.map { it.area }.toIntArray(),
        poiFlags = pois.map { (if (it.streetLevel) 0 else FLAG_NOT_STREET).toByte() }.toByteArray(),
        poiLat = pois.map { e6(it.location.latitude) }.toIntArray(),
        poiLng = pois.map { e6(it.location.longitude) }.toIntArray(),
        roadName = roads.map { it.name }.toTypedArray(),
        roadClass = roads.map { it.roadClass }.toTypedArray(),
        roadStart = starts,
        pointLat = points.map { e6(it.latitude) }.toIntArray(),
        pointLng = points.map { e6(it.longitude) }.toIntArray(),
      )
    }
  }
}

/** 시·구·동 등급(OpenMapTiles place class). 파일의 번호 순서와 같다. */
enum class AreaLevel {
  CITY,
  BOROUGH,
  SUBURB,
  QUARTER,
  NEIGHBOURHOOD,
  OTHER,
}

/**
 * 도로 등급(OpenMapTiles transportation class). 파일의 번호 순서와 같다.
 * [halfWidthMeters] 는 가운데 선에서 차로 끝까지 대략, [dualHalfWidthMeters] 는 상·하행이 따로 그려진 도로의 한쪽 선 기준.
 * [major] = 인도가 따로 있을 만한 큰길.
 */
enum class RoadClass(val halfWidthMeters: Double, val dualHalfWidthMeters: Double, val major: Boolean) {
  MOTORWAY(11.0, 6.0, true),
  TRUNK(9.0, 6.0, true),
  PRIMARY(8.0, 5.5, true),
  SECONDARY(7.0, 5.0, true),
  TERTIARY(5.5, 4.0, true),
  MINOR(3.0, 2.5, false),
  SERVICE(2.5, 2.0, false),
}

/** 검색 순위·핀 설명에서 장소를 얼마나 알아보기 쉬운지. */
internal enum class PoiKind {
  STATION,
  BUS_STOP,
  MAJOR,
  OTHER;

  companion object {
    private val MAJOR_SUBCLASSES =
      setOf("university", "college", "hospital", "department_store", "mall", "townhall", "stadium", "museum", "bus_station", "gate", "zoo", "theme_park")

    fun of(category: String, subclass: String): PoiKind =
      when {
        category == "railway" -> STATION
        category == "bus" && subclass == "bus_stop" -> BUS_STOP
        subclass in MAJOR_SUBCLASSES -> MAJOR
        else -> OTHER
      }
  }
}

/** 검색용 글자 다듬기. */
internal object PlaceText {
  private const val IGNORED = ".,·・()[]'\"/_-"

  /** 공백·기호를 빼고 소문자로 — "연세 대" == "연세대", "신촌오거리.현대백화점" == "신촌오거리현대백화점". */
  fun key(text: String): String =
    buildString(text.length) {
      for (c in text) if (!c.isWhitespace() && c !in IGNORED) append(c.lowercaseChar())
    }

  /** [key] 와 같되, 바뀐 게 없으면 원래 문자열을 그대로(메모리 아끼기). */
  fun keyOrSame(text: String): String = key(text).let { if (it == text) text else it }
}

// ── 좌표 도우미 (1e-6 도 정수)

internal const val M_PER_E6 = 0.111195 // 위도 1e-6 도 ≈ 0.111 m

internal fun e6(degrees: Double): Int = (degrees * 1e6).roundToInt()

internal fun latLng(lat: Int, lng: Int) = LatLng(lat / 1e6, lng / 1e6)

internal fun cosOf(latE6: Int): Double = cos(Math.toRadians(latE6 / 1e6))

internal fun meters(lat1: Int, lng1: Int, lat2: Int, lng2: Int, cosLat: Double): Double {
  val dy = (lat2 - lat1) * M_PER_E6
  val dx = (lng2 - lng1) * M_PER_E6 * cosLat
  return sqrt(dx * dx + dy * dy)
}

private fun latSpan(radiusMeters: Double) = ceil(radiusMeters / M_PER_E6).toInt()

private fun lngSpan(radiusMeters: Double, cosLat: Double) = ceil(radiusMeters / (M_PER_E6 * abs(cosLat).coerceAtLeast(0.01))).toInt()

private class Bounds {
  var minLat = Int.MAX_VALUE
  var minLng = Int.MAX_VALUE
  var maxLat = Int.MIN_VALUE
  var maxLng = Int.MIN_VALUE

  fun add(lat: Int, lng: Int) {
    if (lat < minLat) minLat = lat
    if (lat > maxLat) maxLat = lat
    if (lng < minLng) minLng = lng
    if (lng > maxLng) maxLng = lng
  }

  val empty get() = minLat > maxLat
}

/** 칸마다 항목 번호를 모아 둔 색인(CSR). 칸 크기는 1e-6 도 1000(약 110 m)부터, 칸이 너무 많으면 키운다. */
private class CellGrid(
  private val minLat: Int,
  private val minLng: Int,
  private val cell: Int,
  private val rows: Int,
  private val cols: Int,
  private val start: IntArray,
  private val items: IntArray,
) {
  /** [lat]·[lng] 에서 위도 ±[dLat] · 경도 ±[dLng] 안에 걸친 칸의 항목(한 항목이 여러 번 나올 수 있다). */
  fun forEachNear(lat: Int, lng: Int, dLat: Int, dLng: Int, action: (Int) -> Unit) {
    if (rows == 0) return
    val r0 = Math.floorDiv(lat - dLat - minLat, cell).coerceAtLeast(0)
    val r1 = Math.floorDiv(lat + dLat - minLat, cell).coerceAtMost(rows - 1)
    val c0 = Math.floorDiv(lng - dLng - minLng, cell).coerceAtLeast(0)
    val c1 = Math.floorDiv(lng + dLng - minLng, cell).coerceAtMost(cols - 1)
    for (r in r0..r1) {
      for (c in c0..c1) {
        val k = r * cols + c
        for (j in start[k] until start[k + 1]) action(items[j])
      }
    }
  }

  companion object {
    private const val MAX_CELLS = 2_000_000

    /** [box] 가 항목 i 의 (남, 서, 북, 동)을 채우고 true, 넣지 않을 항목이면 false. */
    fun build(bounds: Bounds, count: Int, box: (Int, IntArray) -> Boolean): CellGrid {
      if (bounds.empty || count == 0) return CellGrid(0, 0, 1, 0, 0, IntArray(1), IntArray(0))
      var cell = 1000
      while (((bounds.maxLat - bounds.minLat) / cell + 1).toLong() * ((bounds.maxLng - bounds.minLng) / cell + 1) > MAX_CELLS) cell *= 2
      val rows = (bounds.maxLat - bounds.minLat) / cell + 1
      val cols = (bounds.maxLng - bounds.minLng) / cell + 1
      val start = IntArray(rows * cols + 1)
      val b = IntArray(4)
      // 1) 칸마다 개수
      for (i in 0 until count) {
        if (!box(i, b)) continue
        for (r in (b[0] - bounds.minLat) / cell..(b[2] - bounds.minLat) / cell) {
          for (c in (b[1] - bounds.minLng) / cell..(b[3] - bounds.minLng) / cell) start[r * cols + c + 1]++
        }
      }
      for (k in 1 until start.size) start[k] += start[k - 1]
      // 2) 채우기
      val items = IntArray(start.last())
      val fill = start.copyOf()
      for (i in 0 until count) {
        if (!box(i, b)) continue
        for (r in (b[0] - bounds.minLat) / cell..(b[2] - bounds.minLat) / cell) {
          for (c in (b[1] - bounds.minLng) / cell..(b[3] - bounds.minLng) / cell) items[fill[r * cols + c]++] = i
        }
      }
      return CellGrid(bounds.minLat, bounds.minLng, cell, rows, cols, start, items)
    }
  }
}
