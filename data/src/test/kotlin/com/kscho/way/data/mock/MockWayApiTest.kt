package com.kscho.way.data.mock

import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.request.NotificationType
import com.kscho.way.data.response.SharingWire
import com.kscho.way.domain.model.geo.Geo
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockWayApiTest {
  private fun epoch(text: String) = LocalDateTime.parse(text).atZone(MockWorld.ZONE).toInstant().toEpochMilli()

  private val request =
    CreatePickupRequest(
      passengerName = "이서연",
      passengerCallName = "서연",
      placeName = "신촌 XX 빌라",
      pickupPoint = MockWorld.VILLA_ALLEY_ENTRANCE.dto(),
      pickupTitle = "신촌 XX 빌라 골목 입구 앞",
      pickupDetail = "신촌로 · 도로 북쪽(빌라 쪽) 인도",
      arrivalAtEpochMillis = epoch("2026-11-09T09:50:00"),
      vehicleDescription = "흰색 아반떼",
      plateNumber = "12가 3456",
      driverName = "김도윤",
      driverCallName = "도윤",
    )

  private fun TestScope.api(scenario: MockPassengerScenario = MockPassengerScenario()) =
    MockWayApi(backgroundScope, clock = { testScheduler.currentTime }, scenario = scenario)

  @Test
  fun search_findsByNameAndByAddress() = runTest {
    val api = api()
    assertEquals("신촌 XX 빌라", api.searchPlaces("신촌xx").first().name)
    assertEquals("신촌 XX 빌라", api.searchPlaces("신촌로 25길").single().name)
    assertTrue(api.searchPlaces("   ").isEmpty())
  }

  @Test
  fun zones_carryRadiusAndSchedule_forTheAppToJudge() = runTest {
    val zones = api().stopZones(MockWorld.BUS_STOP.dto(), 300).associateBy { it.id }
    val busStop = zones.getValue("bus-stop")
    assertEquals(12.0, busStop.radiusMeters, 0.0)
    assertNull(busStop.days)
    val curb = zones.getValue("south-curb")
    assertEquals(listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), curb.days)
    assertEquals(7 * 60, curb.startMinute)
    assertEquals(22 * 60, curb.endMinute)
  }

  @Test
  fun describe_namesAlleyEntranceAndRoadSide() = runTest {
    val d = api().describeLocation(MockWorld.VILLA_ALLEY_ENTRANCE.dto())
    assertEquals("신촌 XX 빌라 골목 입구 앞", d.title)
    assertEquals("신촌로 · 도로 북쪽(빌라 쪽) 인도", d.detail)
  }

  @Test
  fun create_textsLinkThenPassengerOpensAndWalksAfterArrivingSoon() = runTest {
    val api = api()
    val created = api.createPickup(request)
    assertTrue(api.messages.value.single().text.contains("12가 3456"))
    assertTrue(api.messages.value.single().text.contains("09:50"))

    val status = api.passengerUpdates(created.pickupId)
    advanceTimeBy(4_100)
    runCurrent()
    var now = (status as kotlinx.coroutines.flow.StateFlow).value
    assertTrue(now.linkOpened)
    assertEquals(SharingWire.SHARING, now.sharing)
    val home = now.location!!.domain()
    assertEquals(130.0, Geo.distanceMeters(home, MockWorld.VILLA_ALLEY_ENTRANCE), 1.0)

    advanceTimeBy(30_000)
    assertEquals(home, status.value.location!!.domain())

    api.sendNotification(created.pickupId, NotificationRequest(NotificationType.ARRIVING_SOON, request.arrivalAtEpochMillis))
    assertTrue(api.messages.value.last().text.startsWith("[WAY] 곧 도착해요"))
    advanceTimeBy(10_100)
    now = status.value
    assertEquals(130.0 - 13.0, Geo.distanceMeters(now.location!!.domain(), MockWorld.VILLA_ALLEY_ENTRANCE), 1.5)
  }

  @Test
  fun etaChanged_textsBeforeAndAfter() = runTest {
    val api = api()
    val id = api.createPickup(request).pickupId
    api.sendNotification(id, NotificationRequest(NotificationType.ETA_CHANGED, epoch("2026-11-09T09:55:00"), request.arrivalAtEpochMillis))
    assertTrue(api.messages.value.last().text.startsWith("[WAY] 도착 시간이 09:50 → 09:55로 바뀌었어요."))
  }

  @Test
  fun declined_hidesLocation() = runTest {
    val api = api(MockPassengerScenario(sharesLocation = false))
    val id = api.createPickup(request).pickupId
    advanceTimeBy(5_000)
    val now = (api.passengerUpdates(id) as kotlinx.coroutines.flow.StateFlow).value
    assertEquals(SharingWire.DECLINED, now.sharing)
    assertNull(now.location)
  }

  @Test
  fun complete_endsSharing() = runTest {
    val api = api()
    val id = api.createPickup(request).pickupId
    val status = api.passengerUpdates(id) as kotlinx.coroutines.flow.StateFlow
    api.reportArrival(id, ArrivalRequest(testScheduler.currentTime, 60))
    api.completePickup(id, CompletePickupRequest(boarded = true, waitedSeconds = 38, pickupLocationChanges = 0, boardingSource = "DOOR"))
    assertEquals(SharingWire.ENDED, status.value.sharing)
    assertNull(status.value.location)
    assertTrue(api.messages.value.all { it.to == "이서연" })
  }
}
