package com.kscho.way.domain.usecase

import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.model.RoutePreview
import com.kscho.way.domain.model.StopRestriction
import com.kscho.way.domain.pickup.FakeNavigator
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.repository.StopRestrictionRepository
import kotlinx.coroutines.test.runTest
import com.kscho.way.domain.model.ZoneSchedule
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckPickupPointTest {
  private val villa = Place("v", "신촌 XX 빌라", "서울 서대문구", LatLng(37.55581, 126.93900))
  private val asked = mutableListOf<LatLng>()

  private val places =
    object : PlaceRepository {
      override suspend fun search(query: String) = emptyList<Place>()

      override suspend fun describe(location: LatLng) = LocationDescription("골목 입구 앞", "신촌로 · 도로 북쪽 인도")
    }

  /** 서버는 구간 데이터만 — 핀 자리에 걸친 구간 하나를 준다. */
  private fun restrictions(vararg zones: RestrictedZone) =
    object : StopRestrictionRepository {
      override suspend fun zonesNear(location: LatLng, radiusMeters: Int): List<RestrictedZone> {
        asked += location
        return zones.toList()
      }
    }

  private fun zoneAt(at: LatLng, schedule: ZoneSchedule? = null) =
    RestrictedZone("z", "버스정류장", "절대 주정차 금지", listOf(Geo.offset(at, -10.0, 0.0), Geo.offset(at, 10.0, 0.0)), radiusMeters = 8.0, schedule = schedule)

  @Test
  fun check_describesPointMeasuresDistanceAndUsesArrivalTime() = runTest {
    val pin = Geo.offset(villa.location, east = 0.0, north = -40.0)
    val check = CheckPickupPoint(places, restrictions(), FakeNavigator(RoutePreview(12_400, 40 * 60)), clock = { 1_000L })(pin, villa)
    assertEquals("골목 입구 앞", check.point.description.title)
    assertEquals(40, check.point.distanceFromPlaceMeters)
    assertEquals(1_000L + 40 * 60_000L, check.arrivalAtMillis)
    assertEquals(listOf(pin), asked)
    assertEquals(StopRestriction.NotFound, check.restriction)
    assertTrue(check.canConfirm)
  }

  @Test
  fun restriction_isJudgedAtArrivalTimeNotNow() = runTest {
    // 지금 1970-01-01(목) 09:00 KST, 40분 뒤 도착 — 09:30~10:00 에만 규제하는 구간
    val thursdayMorning = ZoneSchedule(setOf(DayOfWeek.THURSDAY), 9 * 60 + 30, 10 * 60)
    val pin = villa.location
    val check = CheckPickupPoint(places, restrictions(zoneAt(pin, thursdayMorning)), FakeNavigator(RoutePreview(12_400, 40 * 60)), clock = { 1_000L })(pin, null)
    assertTrue(check.restriction is StopRestriction.NoStopping)
  }

  @Test
  fun noStoppingZone_cannotBeConfirmed() = runTest {
    val check = CheckPickupPoint(places, restrictions(zoneAt(villa.location)), FakeNavigator(), clock = { 0L })(villa.location, null)
    assertFalse(check.canConfirm)
    assertEquals(null, check.point.distanceFromPlaceMeters)
  }
}
