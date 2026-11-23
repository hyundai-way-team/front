package com.kscho.way.data.mapper

import com.kscho.way.data.response.StopZoneResponse
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.model.restriction.ZoneSchedule
import java.time.DayOfWeek

internal fun StopZoneResponse.toDomain(): RestrictedZone {
  val start = startMinute
  val end = endMinute
  val schedule =
    if (days != null && start != null && end != null) ZoneSchedule(days.mapNotNull { d -> DayOfWeek.entries.firstOrNull { it.name == d } }.toSet(), start, end)
    else null
  return RestrictedZone(id, name, rule, path.map { it.toDomain() }, radiusMeters, schedule)
}
