package com.kscho.way.domain.model.restriction

import com.kscho.way.domain.model.geo.LatLng
import java.time.DayOfWeek
import java.time.ZonedDateTime

data class ZoneSchedule(val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int) {
  fun isActive(at: ZonedDateTime): Boolean {
    val minute = at.hour * 60 + at.minute
    return at.dayOfWeek in days && minute >= startMinute && minute < endMinute
  }
}

data class RestrictedZone(
  val id: String,
  val name: String,
  val rule: String,
  val path: List<LatLng>,
  val radiusMeters: Double = 10.0,
  val schedule: ZoneSchedule? = null,
)

sealed interface StopRestriction {
  data object NotFound : StopRestriction

  data class NoStopping(val zone: RestrictedZone) : StopRestriction
}
