package com.kscho.way.server.zones

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.response.StopZoneResponse
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.server.kakao.PlaceFinder
import com.kscho.way.server.kakao.Region
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 지정 주정차 금지구역 — 공공데이터(도로명·상세위치 글·시간대) + 끝점은 그때그때 카카오 로컬 API 로.
 *
 * 카카오 정책상 찾은 좌표를 저장해 다시 쓰면 안 돼서 물을 때마다 실시간으로 찾는다(캐시 없음). 호출을 줄이려고
 *  1) OSM 도로 선으로 화면 안을 지나는 도로의 금지구역만 고르고(없으면 카카오를 부르지 않는다),
 *  2) 화면 가운데의 시군구와 같은 곳만, 가까운 도로 순으로 [maxRows] 곳까지만 찾는다.
 * 끝점 둘은 그 도로 위에 맞춰 사이를 도로를 따라 자른다. 끝점을 못 찾거나 도로에서 멀면(엉뚱한 가게) 그 구역은 그리지 않는다.
 */
class DesignatedZones(
  rows: List<NoParkingRow>,
  private val roads: RoadIndex,
  private val finder: PlaceFinder,
  private val maxRows: Int = MAX_ROWS,
  private val parallel: Int = PARALLEL,
) {
  private val byRoad: Map<String, List<NoParkingRow>> = rows.flatMap { r -> r.roads.map { it to r } }.groupBy({ it.first }, { it.second })

  /** 그릴 수 있는 줄만(시간 창이 있고, 처음·끝 끝점 모두 물을 말이 있음) — 못 그릴 줄이 상한 자리를 먹지 않게 미리 거른다. */
  private val drawable: Set<String> = rows.filter(::isDrawable).map { it.id }.toHashSet()

  val size: Int = rows.size

  suspend fun near(lat: Double, lng: Double, radiusMeters: Int): List<StopZoneResponse> {
    val visible = roads.near(lat, lng, radiusMeters.toDouble())
    if (visible.isEmpty()) return emptyList()
    val roadDistance = HashMap<String, Double>()
    for ((road, d) in visible) roadDistance.merge(road.name, d, ::minOf)
    val candidates = roadDistance.keys.flatMap { byRoad[it].orEmpty() }.distinctBy { it.id }.filter { it.id in drawable }
    if (candidates.isEmpty()) return emptyList()

    val region = finder.region(lat, lng) ?: return emptyList()
    // 긴 큰길은 한 길에 금지구역이 수십 곳이라 '가까운 길'만으로는 화면 안 구역을 못 고른다 —
    // 상세위치 글에 이 자리 동 이름('매탄3동 …', '백석동 …')이 든 곳을 먼저
    val stems = region.dongs.mapNotNull(::dongStem).distinct()
    val picked =
      candidates
        .filter { it.isIn(region) }
        .sortedWith(compareBy<NoParkingRow>({ r -> stems.none { r.desc.contains(it) } }, { r -> r.roads.minOf { roadDistance[it] ?: Double.MAX_VALUE } }))
        .take(maxRows)
    if (picked.isEmpty()) return emptyList()

    // 끝점은 화면 밖에 있어도 된다 — 긴 구역(총연장)일수록 멀리까지(핀 확인처럼 반경이 작은 물음도 지도와 같은 구역을 내도록)
    val reach = picked.associate { it.id to searchRadius(it, radiusMeters) }
    val area = roads.near(lat, lng, reach.values.max().toDouble())
    val gate = Semaphore(parallel)
    return coroutineScope {
      picked
        .map { row ->
          val r = reach.getValue(row.id)
          val chains = area.filter { (c, d) -> d <= r && c.name in row.roads }.map { it.first }
          async { gate.withPermit { zonesOf(row, chains, lat, lng, r) } }
        }
        .awaitAll()
        .flatten()
    }
  }

  private suspend fun zonesOf(row: NoParkingRow, chains: List<RoadIndex.Road>, lat: Double, lng: Double, searchRadius: Int): List<StopZoneResponse> {
    if (chains.isEmpty()) return emptyList()
    val windows = NoParkingSchedule.windows(row)
    if (windows.isEmpty()) return emptyList()
    val path = pathOf(row, chains, lat, lng, searchRadius) ?: return emptyList()
    val name = "${row.desc} (${row.roadRaw})"
    val rule = NoParkingSchedule.describe(row)
    val radius = path.second
    if (NoParkingSchedule.isAlways(windows)) return listOf(StopZoneResponse(row.id, name, rule, path.first, radius))
    return windows.mapIndexed { i, w ->
      StopZoneResponse(
        id = "${row.id}-$i",
        name = name,
        rule = rule,
        path = path.first,
        radiusMeters = radius,
        days = w.days.map { it.name },
        startMinute = w.startMinute,
        endMinute = w.endMinute,
      )
    }
  }

  /** 금지구역 선(도로를 따라)과 판정 반폭. 못 그리면 null. */
  private suspend fun pathOf(row: NoParkingRow, chains: List<RoadIndex.Road>, lat: Double, lng: Double, searchRadius: Int): Pair<List<LatLngDto>, Double>? {
    val texts = NoParkingText.endpoints(row.desc)
    if (texts.isEmpty()) return null
    val length = row.lengthMeters
    if (texts.size == 1) {
      val at = locate(texts.single(), null, row, chains, lat, lng, searchRadius) ?: return null
      return around(at, length)
    }
    // 가운데 들른 곳('A ∼ B ∼ C')은 빼고 처음과 끝만
    val a = locate(texts.first(), null, row, chains, lat, lng, searchRadius) ?: return null
    val b = locate(texts.last(), texts.first(), row, chains, lat, lng, searchRadius) ?: return null
    val gap = Geo.distanceMeters(a.point.ll(), b.point.ll())
    if (gap < MIN_LENGTH_METERS) return around(a, length)
    // 두 끝이 함께 올라앉는 선(상·하행이 따로 그려진 길이면 둘 다 가까운 쪽). 도로를 따라 잰 길이가
    // 두 끝 사이 거리보다 터무니없이 길면(갔다가 되돌아오는 선) 그 선은 쓰지 않는다
    val along =
      chains
        .map { c -> Triple(c, RoadIndex.snap(c, a.point.lat, a.point.lng), RoadIndex.snap(c, b.point.lat, b.point.lng)) }
        .filter { (c, sa, sb) -> sa.distance <= SNAP_METERS + c.halfWidthMeters && sb.distance <= SNAP_METERS + c.halfWidthMeters }
        .sortedBy { (_, sa, sb) -> sa.distance + sb.distance }
        .map { (c, sa, sb) -> RoadIndex.cut(c, sa.along, sb.along) to c }
        .firstOrNull { (path, _) -> pathLength(path).let { it <= maxOf(gap * FOLD_RATIO, gap + FOLD_SLACK_METERS) } }
    val path =
      along
        ?: run {
          // 같은 이름 길이 끊겨 있으면 짧은 곳만 곧게 잇는다
          if (gap > STRAIGHT_MAX_METERS) return null
          listOf(a.point, b.point) to a.road
        }
    val drawn = pathLength(path.first)
    // 데이터의 총연장보다 터무니없이 길면 끝점을 잘못 찾은 것
    if (length != null && drawn > maxOf(length * 3, length + 300.0)) return null
    if (length == null && drawn > MAX_PAIR_WITHOUT_LENGTH_METERS) return null
    return line(path.first, path.second)
  }

  /** 끝점 하나 둘레로 총연장만큼(없으면 짧게, 길어도 [MAX_SINGLE_METERS] 까지 — 어느 쪽으로 뻗는지 모르니 양쪽 반씩). */
  private fun around(at: RoadIndex.Snap, length: Double?): Pair<List<LatLngDto>, Double>? {
    val half = (length ?: SINGLE_DEFAULT_METERS).coerceIn(MIN_LENGTH_METERS, MAX_SINGLE_METERS) / 2
    return line(RoadIndex.cut(at.road, at.along - half, at.along + half), at.road)
  }

  private fun line(path: List<LatLngDto>, road: RoadIndex.Road): Pair<List<LatLngDto>, Double>? =
    if (path.size >= 2) path to road.halfWidthMeters else null

  /** 끝점 글 → 도로 위 자리. 카카오가 준 후보 가운데 이 도로에 가장 가까운 것, 도로에서 멀면 다음 물음. */
  private suspend fun locate(text: String, previous: String?, row: NoParkingRow, chains: List<RoadIndex.Road>, lat: Double, lng: Double, searchRadius: Int): RoadIndex.Snap? {
    for (lookup in NoParkingText.lookups(text, row, previous)) {
      val found =
        when (lookup) {
          is NoParkingText.Lookup.Address -> finder.address(lookup.query)
          is NoParkingText.Lookup.Keyword -> finder.keyword(lookup.query, lat, lng, searchRadius)
        }
      val best =
        found
          .flatMap { p -> chains.map { c -> RoadIndex.snap(c, p.lat, p.lng) } }
          .filter { it.distance <= SNAP_METERS + it.road.halfWidthMeters }
          .minByOrNull { it.distance }
      if (best != null) return best
    }
    return null
  }

  private fun NoParkingRow.isIn(region: Region): Boolean {
    if (sido.take(2) != region.sido.take(2)) return false
    if (region.code.length >= 5 && sigunguCode == region.code.take(5)) return true
    val mine = sigungu.substringBefore(' ')
    val there = region.sigungu.substringBefore(' ')
    return mine.isNotEmpty() && mine == there
  }

  private fun LatLngDto.ll() = LatLng(lat, lng)

  private fun isDrawable(row: NoParkingRow): Boolean {
    if (NoParkingSchedule.windows(row).isEmpty()) return false
    val texts = NoParkingText.endpoints(row.desc)
    if (texts.isEmpty()) return false
    if (NoParkingText.lookups(texts.first(), row).isEmpty()) return false
    return texts.size == 1 || NoParkingText.lookups(texts.last(), row, texts.first()).isNotEmpty()
  }

  /** 끝점 찾는 반경 — 화면 반경 + 구역 길이만큼(최소 1 km 더), 카카오 상한 20 km. */
  private fun searchRadius(row: NoParkingRow, radiusMeters: Int): Int =
    (radiusMeters + maxOf(MARGIN_METERS, (row.lengthMeters ?: 0.0) + 500.0)).toInt().coerceAtMost(20_000)

  private fun pathLength(path: List<LatLngDto>): Double = path.zipWithNext { p, q -> Geo.distanceMeters(p.ll(), q.ll()) }.sum()

  companion object {
    /** '매탄3동' → '매탄', '성수1가제1동' → '성수', '백석동' → '백석'. 두 글자 안 되면 null. */
    fun dongStem(name: String): String? {
      val stem = name.trim().replace(Regex("(\\d+가)?(제?\\d+)?(동|가|리|읍|면)$"), "").trim()
      return stem.takeIf { it.length >= 2 }
    }

    /** 한 번 물을 때 끝점을 찾을 금지구역 수(카카오 호출 = 대략 이것의 2~4배 + 1). */
    const val MAX_ROWS = 24
    const val PARALLEL = 6
    /** 카카오 후보가 도로 가장자리에서 이만큼 안이면 그 도로의 점으로 본다(가게 좌표는 건물 안이라 길에서 조금 떨어져 있다). */
    const val SNAP_METERS = 60.0
    const val MARGIN_METERS = 1_000.0
    const val MIN_LENGTH_METERS = 20.0
    /** 끝점이 하나뿐인 구역을 그리는 최대 길이(어느 쪽으로 뻗는지 몰라 양쪽 반씩). */
    const val MAX_SINGLE_METERS = 300.0
    /** 총연장이 없는 두 끝점 구역의 최대 길이. */
    const val MAX_PAIR_WITHOUT_LENGTH_METERS = 4_000.0
    const val STRAIGHT_MAX_METERS = 250.0
    /** 도로를 따라 잰 길이가 두 끝 사이 거리의 이 배 + 여유를 넘으면 되돌아가는 선으로 본다. */
    const val FOLD_RATIO = 2.5
    const val FOLD_SLACK_METERS = 150.0
    /** 끝점이 하나인데 총연장도 없을 때. */
    const val SINGLE_DEFAULT_METERS = 30.0
  }
}
