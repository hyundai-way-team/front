package com.kscho.way.domain.model

/**
 * 지도에 그리는 정차 금지 구간은 픽업 위치 둘레만 — 홈·오는 길 곳곳의 구간은 운전자에게 필요 없다.
 * 약속 장소 검색(D-03) · 핀 맞추기(D-04) · 주행·정차(D-06~D-09)가 같은 둘레를 쓴다.
 */
object PickupZones {
  /** 정차 화면(D-08, 1dp = 0.3 m)에서 보이는 가장 먼 모서리쯤. */
  const val RADIUS_METERS = 300

  /** 픽업 위치에서 [radiusMeters] 안에 걸치는 구간(선이면 가장 가까운 곳 기준, 구간 폭 포함). */
  fun around(pickup: LatLng, zones: List<RestrictedZone>, radiusMeters: Int = RADIUS_METERS): List<RestrictedZone> =
    zones.filter { Geo.distanceToPathMeters(pickup, it.path) - it.radiusMeters <= radiusMeters }

  /** 핀 둘레를 받아 둘 곳 — 둘레보다 넉넉히 받아 핀을 조금 옮길 때마다 다시 묻지 않게. */
  fun areaFor(pickup: LatLng): MapArea = MapArea.around(pickup, RADIUS_METERS)

  /** 핀을 옮겨 핀 둘레가 받아 둔 곳 밖으로 나가면 다시 받는다. */
  fun needsReload(loaded: MapArea?, pickup: LatLng): Boolean = loaded?.covers(pickup, RADIUS_METERS) != true
}
