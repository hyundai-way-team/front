package com.kscho.way.data.place

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.LocationDescription
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object LocationWording {
  const val LANDMARK_METERS = 30.0
  const val ROAD_SEARCH_METERS = 150.0
  const val ROADSIDE_METERS = 30.0

  private const val DUAL_PARTNER_METERS = 45.0

  internal class RoadContext(
    val hit: PlaceIndex.RoadHit,
    val name: String,
    val roadClass: RoadClass,
    val edgeMeters: Double,
    val onRoad: Boolean,
    val side: String,
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

  internal fun roadContext(index: PlaceIndex, hits: List<PlaceIndex.RoadHit>): RoadContext? {
    val best = hits.minByOrNull { it.meters - index.roadClass[it.road].halfWidthMeters } ?: return null
    val name = index.roadName[best.road]
    val roadClass = index.roadClass[best.road]
    val partners =
      hits.filter {
        it.road != best.road &&
          index.roadName[it.road] == name &&
          it.meters <= DUAL_PARTNER_METERS &&
          distance(it.east - best.east, it.north - best.north) > 6.0
      }
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

  private fun weight(kind: PoiKind) =
    when (kind) {
      PoiKind.BUS_STOP -> 0.7
      PoiKind.STATION,
      PoiKind.MAJOR -> 0.8
      PoiKind.OTHER -> 1.0
    }

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
