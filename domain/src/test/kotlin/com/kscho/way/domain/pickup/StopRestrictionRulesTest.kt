package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.model.restriction.ZoneSchedule
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopRestrictionRulesTest {
  private val seoul = ZoneId.of("Asia/Seoul")

  private fun at(text: String) = LocalDateTime.parse(text).atZone(seoul).toInstant().toEpochMilli()

  private val busStopAt = LatLng(37.556783, 126.942649)
  private val busStop =
    RestrictedZone("bus-stop", "버스정류장", "절대 주정차 금지 · 상시", listOf(Geo.offset(busStopAt, -20.0, 0.0), Geo.offset(busStopAt, 20.0, 0.0)), radiusMeters = 12.0)
  private val curbAt = LatLng(37.5562, 126.9420)
  private val curb =
    RestrictedZone(
      "south-curb",
      "신촌로 남쪽 길가",
      "주정차 금지 · 평일 07:00~22:00",
      listOf(Geo.offset(curbAt, -30.0, 0.0), Geo.offset(curbAt, 30.0, 0.0)),
      radiusMeters = 6.0,
      schedule = ZoneSchedule(days = DayOfWeek.entries.filter { it < DayOfWeek.SATURDAY }.toSet(), startMinute = 7 * 60, endMinute = 22 * 60),
    )
  private val zones = listOf(busStop, curb)

  @Test
  fun alwaysZone_isRestrictedAnyTime() {
    val result = StopRestrictionRules.check(busStopAt, at("2026-11-14T23:30:00"), zones, seoul)
    assertEquals(StopRestriction.NoStopping(busStop), result)
  }

  @Test
  fun outsideRadius_isNotFound() {
    val nearby = Geo.offset(busStopAt, 0.0, 30.0)
    assertEquals(StopRestriction.NotFound, StopRestrictionRules.check(nearby, at("2026-11-09T09:50:00"), zones, seoul))
  }

  @Test
  fun timedZone_followsArrivalTimeInKorea() {
    assertTrue(StopRestrictionRules.check(curbAt, at("2026-11-09T09:50:00"), zones, seoul) is StopRestriction.NoStopping)
    assertEquals(StopRestriction.NotFound, StopRestrictionRules.check(curbAt, at("2026-11-09T22:30:00"), zones, seoul))
    assertEquals(StopRestriction.NotFound, StopRestrictionRules.check(curbAt, at("2026-11-14T09:50:00"), zones, seoul))
  }
}
