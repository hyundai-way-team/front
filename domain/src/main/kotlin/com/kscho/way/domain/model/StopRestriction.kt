package com.kscho.way.domain.model

import java.time.DayOfWeek
import java.time.ZonedDateTime

/** 규제 시간(요일 · 하루 중 시작~끝 분). 없으면 상시. */
data class ZoneSchedule(val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int) {
  fun isActive(at: ZonedDateTime): Boolean {
    val minute = at.hour * 60 + at.minute
    return at.dayOfWeek in days && minute >= startMinute && minute < endMinute
  }
}

/**
 * 정차 금지 구간(버스정류장·횡단보도 등) — 서버가 주는 데이터. [rule] 예) "절대 주정차 금지 · 상시"
 * [path] 에서 [radiusMeters] 안이면 구간 안, [schedule] 이 없으면 상시.
 */
data class RestrictedZone(
  val id: String,
  val name: String,
  val rule: String,
  val path: List<LatLng>,
  val radiusMeters: Double = 10.0,
  val schedule: ZoneSchedule? = null,
)

/** 도착 예정 시각 기준 정차 규제 확인 결과. */
sealed interface StopRestriction {
  /** 데이터에서 규제를 찾지 못함 — 데이터에 없는 규제가 있을 수 있어 현장 표지판 확인을 안내한다. */
  data object NotFound : StopRestriction

  data class NoStopping(val zone: RestrictedZone) : StopRestriction
}
