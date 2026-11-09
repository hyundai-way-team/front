package com.kscho.way.domain.usecase

import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.MapFeatures
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.model.RoutePreview
import com.kscho.way.domain.model.StopRestriction
import com.kscho.way.domain.pickup.FakeNavigator
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.repository.StopRestrictionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckPickupPointTest {
  private val villa = Place("v", "신촌 XX 빌라", "서울 서대문구", LatLng(37.55581, 126.93900))
  private val zone = RestrictedZone("z", "버스정류장", "절대 주정차 금지 · 상시", emptyList())
  private val checkedAt = mutableListOf<Long>()

  private val places =
    object : PlaceRepository {
      override suspend fun search(query: String) = emptyList<Place>()

      override suspend fun describe(location: LatLng) = LocationDescription("골목 입구 앞", "신촌로 · 도로 북쪽 인도")

      override suspend fun mapFeatures(center: LatLng, radiusMeters: Int) = MapFeatures(emptyList(), emptyList(), emptyList())
    }

  private fun restrictions(blocked: Boolean) =
    object : StopRestrictionRepository {
      override suspend fun check(location: LatLng, arrivalAtMillis: Long): StopRestriction {
        checkedAt += arrivalAtMillis
        return if (blocked) StopRestriction.NoStopping(zone) else StopRestriction.NotFound
      }
    }

  @Test
  fun check_describesPointMeasuresDistanceAndUsesArrivalTime() = runTest {
    val pin = Geo.offset(villa.location, east = 0.0, north = -40.0)
    val check = CheckPickupPoint(places, restrictions(false), FakeNavigator(RoutePreview(12_400, 40 * 60)), clock = { 1_000L })(pin, villa)
    assertEquals("골목 입구 앞", check.point.description.title)
    assertEquals(40, check.point.distanceFromPlaceMeters)
    assertEquals(1_000L + 40 * 60_000L, check.arrivalAtMillis)
    assertEquals(listOf(check.arrivalAtMillis), checkedAt) // 규제는 도착 예정 시각 기준
    assertTrue(check.canConfirm)
  }

  @Test
  fun noStoppingZone_cannotBeConfirmed() = runTest {
    val check = CheckPickupPoint(places, restrictions(true), FakeNavigator(), clock = { 0L })(villa.location, null)
    assertFalse(check.canConfirm)
    assertEquals(null, check.point.distanceFromPlaceMeters)
  }
}
