package com.kscho.way.domain.usecase

import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.model.StopRestriction
import java.time.Instant
import java.time.ZoneId

/**
 * 정차 금지 판단(기획 3-2 · D-04 경고) — 앱이 한다. 서버는 근처 구간 데이터만 준다.
 * 핀이 구간 반경 안이고, 도착 예정 시각(한국 시간)에 규제 시간이면 정차 금지.
 */
object StopRestrictionRules {
  val KOREA: ZoneId = ZoneId.of("Asia/Seoul")

  fun check(location: LatLng, arrivalAtMillis: Long, zones: List<RestrictedZone>, zone: ZoneId = KOREA): StopRestriction {
    val at = Instant.ofEpochMilli(arrivalAtMillis).atZone(zone)
    val hit =
      zones.firstOrNull { z ->
        z.path.isNotEmpty() && Geo.distanceToPathMeters(location, z.path) <= z.radiusMeters && (z.schedule?.isActive(at) ?: true)
      }
    return if (hit != null) StopRestriction.NoStopping(hit) else StopRestriction.NotFound
  }
}
