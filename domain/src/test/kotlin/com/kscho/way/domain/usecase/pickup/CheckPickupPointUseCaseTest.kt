package com.kscho.way.domain.usecase.pickup

import com.kscho.way.domain.gateway.Clock
import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.model.restriction.ZoneSchedule
import com.kscho.way.domain.model.route.RoutePreview
import com.kscho.way.domain.pickup.FakeNavigator
import com.kscho.way.domain.repository.place.PlaceRepository
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import java.time.DayOfWeek
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckPickupPointUseCaseTest {
  private val villa = Place("v", "신촌 XX 빌라", "서울 서대문구", LatLng(37.55581, 126.93900))
  private val asked = mutableListOf<LatLng>()

  private val places =
    object : PlaceRepository {
      override suspend fun search(query: String) = emptyList<Place>()

      override suspend fun describe(location: LatLng) = LocationDescription("골목 입구 앞", "신촌로 · 도로 북쪽 인도")
    }

  private fun restrictions(vararg zones: RestrictedZone) =
    object : StopRestrictionRepository {
      override suspend fun zonesNear(location: LatLng, radiusMeters: Int): ApiState<List<RestrictedZone>> {
        asked += location
        return ApiState.Success(zones.toList())
      }
    }

  private fun zoneAt(at: LatLng, schedule: ZoneSchedule? = null) =
    RestrictedZone("z", "버스정류장", "절대 주정차 금지", listOf(Geo.offset(at, -10.0, 0.0), Geo.offset(at, 10.0, 0.0)), radiusMeters = 8.0, schedule = schedule)

  @Test
  fun check_describesPointMeasuresDistanceAndUsesArrivalTime() = runTest {
    val pin = Geo.offset(villa.location, east = 0.0, north = -40.0)
    val check = CheckPickupPointUseCase(places, restrictions(), FakeNavigator(RoutePreview(12_400, 40 * 60)), clock = Clock { 1_000L })(pin, villa)
    assertEquals("골목 입구 앞", check.point.description.title)
    assertEquals(40, check.point.distanceFromPlaceMeters)
    assertEquals(1_000L + 40 * 60_000L, check.arrivalAtMillis)
    assertEquals(listOf(pin), asked)
    assertEquals(StopRestriction.NotFound, check.restriction)
    assertTrue(check.canConfirm)
  }

  @Test
  fun restriction_isJudgedAtArrivalTimeNotNow() = runTest {
    val thursdayMorning = ZoneSchedule(setOf(DayOfWeek.THURSDAY), 9 * 60 + 30, 10 * 60)
    val pin = villa.location
    val check = CheckPickupPointUseCase(places, restrictions(zoneAt(pin, thursdayMorning)), FakeNavigator(RoutePreview(12_400, 40 * 60)), clock = Clock { 1_000L })(pin, null)
    assertTrue(check.restriction is StopRestriction.NoStopping)
  }

  @Test
  fun noStoppingZone_cannotBeConfirmed() = runTest {
    val check = CheckPickupPointUseCase(places, restrictions(zoneAt(villa.location)), FakeNavigator(), clock = Clock { 0L })(villa.location, null)
    assertFalse(check.canConfirm)
    assertEquals(null, check.point.distanceFromPlaceMeters)
  }
}
