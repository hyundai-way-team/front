package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.model.restriction.StopRestriction
import java.time.Instant
import java.time.ZoneId

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
