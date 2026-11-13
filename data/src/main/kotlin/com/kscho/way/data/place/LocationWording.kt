package com.kscho.way.data.place

import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 핀 위치를 사람이 알아보는 말로(역지오코딩) — 앱 속 장소 색인만 쓴다.
 *
 * 제목
 * - 30 m 안 길에서 보이는 장소(길 건너편 것은 빼고): "신촌자이엘라 버스정류장 앞" · "○○ 식당 앞"
 * - 없으면 가장 가까운 이름 있는 도로: 차로 위면 "신촌로 도로 위", 아니면 "신촌로 근처"
 * - 도로도 150 m 안에 없으면 "지도에서 고른 위치"
 *
 * 설명 — 가장 가까운 도로에 대해 핀이 어느 쪽인지(도로선의 법선 방향을 동서남북 넷 중 하나로)
 * - 길가(차로 끝에서 30 m 안): "신촌로 · 도로 북쪽 인도"(큰길) / "연희로11길 · 도로 북쪽 길가"(작은 길)
 * - 차로 위: "신촌로 · 차로 위예요 · 길가로 옮겨 주세요"
 * - 더 떨어짐: "신촌로 북쪽으로 약 60m · 골목 안일 수 있어요"
 *
 * 상·하행이 따로 그려진 도로(신촌로처럼)는 두 선 사이에 핀이 있으면 차로 위로 본다.
 */
internal object LocationWording {
  const val LANDMARK_METERS = 30.0
  const val ROAD_SEARCH_METERS = 150.0
  const val ROADSIDE_METERS = 30.0

  /** 같은 이름의 다른 선(반대 방향 차로)을 찾는 거리 */
  private const val DUAL_PARTNER_METERS = 45.0

  internal class RoadContext(
    val hit: PlaceIndex.RoadHit,
    val name: String,
    val roadClass: RoadClass,
    /** 차로 끝에서 핀까지(m). 0 이하면 차로 안. */
    val edgeMeters: Double,
    val onRoad: Boolean,
    val side: String,
    /** 같은 이름 도로선들(상·하행) — 길 건너편 판단용 */
    val lines: List<Int>,
  )

  fun describe(index: PlaceIndex, p: LatLng): LocationDescription {
    val hits = index.roadsNear(p, ROAD_SEARCH_METERS)
    val road = roadContext(index, hits)
    val landmark = landmark(index, p, road, hits)

    val title =
      when {
        landmark != null -> "${index.poiName[landmark]} 앞"
        road == null -> "지도에서 고른 위치"
        road.onRoad -> "${road.name} 도로 위"
        else -> "${road.name} 근처"
      }
    val detail =
      when {
        road == null -> {
          val dong = index.nearestArea(e6(p.latitude), e6(p.longitude), AreaLevel.QUARTER, maxMeters = 2_000.0)?.let { index.areaName[it] }
          listOfNotNull(dong, "큰길에서 떨어진 곳", "골목 안일 수 있어요").joinToString(" · ")
        }
        road.onRoad -> "${road.name} · 차로 위예요 · 길가로 옮겨 주세요"
        road.edgeMeters <= ROADSIDE_METERS -> "${road.name} · 도로 ${road.side} ${if (road.roadClass.major) "인도" else "길가"}"
        else -> "${road.name} ${road.side}으로 약 ${roundTo10(road.edgeMeters)}m · 골목 안일 수 있어요"
      }
    return LocationDescription(title, detail)
  }

  /** 가장 가까운 이름 있는 도로(차로 끝까지 거리로 — 넓은 길이 조금 더 잘 잡힌다). */
  internal fun roadContext(index: PlaceIndex, hits: List<PlaceIndex.RoadHit>): RoadContext? {
    val best = hits.minByOrNull { it.meters - index.roadClass[it.road].halfWidthMeters } ?: return null
    val name = index.roadName[best.road]
    val roadClass = index.roadClass[best.road]
    // 같은 이름의 다른 선 — 이어지는 조각(가장 가까운 곳이 거의 같은 자리)은 빼고
    val partners =
      hits.filter {
        it.road != best.road &&
          index.roadName[it.road] == name &&
          it.meters <= DUAL_PARTNER_METERS &&
          distance(it.east - best.east, it.north - best.north) > 6.0
      }
    // 두 선이 핀의 반대쪽에 있으면 핀은 상·하행 사이(= 차로 위)
    val between = best.meters > 0.5 && partners.any { cosine(best.east, best.north, it.east, it.north) < -0.3 }
    val halfWidth = if (partners.isNotEmpty()) roadClass.dualHalfWidthMeters else roadClass.halfWidthMeters
    val edge = best.meters - halfWidth
    return RoadContext(
      hit = best,
      name = name,
      roadClass = roadClass,
      edgeMeters = edge,
      onRoad = between || edge <= 0.0,
      side = compass(best.normalEast, best.normalNorth),
      lines = (listOf(best.road) + hits.filter { index.roadName[it.road] == name && it.meters <= DUAL_PARTNER_METERS }.map { it.road }).distinct(),
    )
  }

  /** 30 m 안 길에서 보이는 장소 중 가장 알아보기 좋은 것. 핀과 사이에 큰길이 있으면(길 건너편) 뺀다. */
  private fun landmark(index: PlaceIndex, p: LatLng, road: RoadContext?, hits: List<PlaceIndex.RoadHit>): Int? {
    val nearby = index.poisNear(p, LANDMARK_METERS).filter { index.poiStreetLevel(it.poi) }
    if (nearby.isEmpty()) return null
    val barriers = buildSet {
      if (road != null && road.edgeMeters <= ROADSIDE_METERS + LANDMARK_METERS) addAll(road.lines)
      for (h in hits) if (index.roadClass[h.road].major && h.meters <= LANDMARK_METERS + 10.0) add(h.road)
    }
    return nearby
      .sortedBy { it.meters * weight(index.poiKindOf(it.poi)) }
      .firstOrNull { hit -> barriers.none { index.crossesRoad(p, index.poi(hit.poi).location, it) } }
      ?.poi
  }

  /** 픽업에 쓰기 좋은 곳(정류장·역·정문)은 조금 더 멀어도 고른다. */
  private fun weight(kind: PoiKind) =
    when (kind) {
      PoiKind.BUS_STOP -> 0.7
      PoiKind.STATION,
      PoiKind.MAJOR -> 0.8
      PoiKind.OTHER -> 1.0
    }

  /** 도로에서 핀 쪽을 가리키는 방향 → "북쪽"·"남쪽"·"동쪽"·"서쪽". */
  internal fun compass(east: Double, north: Double): String =
    if (abs(north) >= abs(east)) {
      if (north >= 0) "북쪽" else "남쪽"
    } else {
      if (east >= 0) "동쪽" else "서쪽"
    }

  private fun distance(x: Double, y: Double) = sqrt(x * x + y * y)

  private fun cosine(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val la = distance(ax, ay)
    val lb = distance(bx, by)
    return if (la == 0.0 || lb == 0.0) 1.0 else (ax * bx + ay * by) / (la * lb)
  }

  private fun roundTo10(meters: Double) = ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10)
}
