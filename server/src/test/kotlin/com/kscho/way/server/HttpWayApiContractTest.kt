package com.kscho.way.server

import com.kscho.way.data.mock.MockWorld
import com.kscho.way.data.remote.ArrivalRequest
import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.DriverStatusRequest
import com.kscho.way.data.remote.HttpWayApi
import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.NotificationRequest
import com.kscho.way.data.remote.NotificationType
import com.kscho.way.data.remote.SharingWire
import com.kscho.way.data.remote.WayApiException
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 운전자 앱의 HTTP 구현(data/HttpWayApi)이 이 서버와 그대로 맞물리는지 — 진짜 소켓으로. */
class HttpWayApiContractTest {
  private lateinit var deps: ServerDeps
  private lateinit var server: EmbeddedServer<*, *>
  private lateinit var api: HttpWayApi

  @Before
  fun start() {
    deps = createDeps("http://localhost:8080", mapDir = null)
    server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { wayModule(deps) }.start(wait = false)
    val port = runBlocking { server.engine.resolvedConnectors().first().port }
    api = HttpWayApi("http://127.0.0.1:$port")
  }

  @After
  fun stop() {
    api.close()
    server.stop(0, 0)
  }

  private val request =
    CreatePickupRequest(
      passengerName = "이서연",
      passengerPhone = "01000001234",
      placeName = "신촌 XX 빌라",
      pickupPoint = LatLngDto(MockWorld.VILLA_ALLEY_ENTRANCE.latitude, MockWorld.VILLA_ALLEY_ENTRANCE.longitude),
      pickupTitle = "신촌 XX 빌라 골목 입구 앞",
      pickupDetail = "신촌로 · 도로 북쪽(빌라 쪽) 인도",
      arrivalAtEpochMillis = System.currentTimeMillis() + 600_000,
      vehicleDescription = "흰색 아반떼",
      plateNumber = "12가 3456",
      driverName = "김도윤",
    )

  @Test
  fun placesAndProfile() = runBlocking {
    assertEquals("신촌 XX 빌라", api.searchPlaces("xx").first().name)
    val here = LatLngDto(MockWorld.VILLA_ALLEY_ENTRANCE.latitude, MockWorld.VILLA_ALLEY_ENTRANCE.longitude)
    assertEquals("신촌 XX 빌라 골목 입구 앞", api.describeLocation(here).title)
    assertTrue(api.mapFeatures(here, 300).zones.isNotEmpty())
    val busStop = LatLngDto(MockWorld.BUS_STOP.latitude, MockWorld.BUS_STOP.longitude)
    assertTrue(api.checkStopRestriction(busStop, System.currentTimeMillis()).restricted)
    assertEquals("김도윤", api.myVehicle().driverName)
  }

  @Test
  fun wholePickup_overHttpAndWebSocket() = runBlocking {
    val created = api.createPickup(request)
    val token = created.shareUrl.substringAfterLast("/p/")
    assertTrue(deps.outbox.messages.value.single().text.contains(created.shareUrl))

    // 동승자 웹이 링크를 열고 위치를 공유하면 운전자 앱 흐름으로 온다
    deps.hub.openLink(token)
    deps.hub.setSharing(token, allow = true)
    deps.hub.updateLocation(token, LatLngDto(37.5571, 126.942))
    val status = withTimeout(5_000) { api.passengerUpdates(created.pickupId).first { it.location != null } }
    assertEquals(SharingWire.SHARING, status.sharing)

    api.reportDriver(created.pickupId, DriverStatusRequest(LatLngDto(37.559, 126.96), 600, 1800, request.arrivalAtEpochMillis))
    api.sendNotification(created.pickupId, NotificationRequest(NotificationType.ARRIVING_SOON, request.arrivalAtEpochMillis))
    api.reportArrival(created.pickupId, ArrivalRequest(System.currentTimeMillis(), 60))
    assertEquals("ARRIVED", deps.hub.view(token).phase)
    assertEquals(2, deps.outbox.messages.value.size) // 출발 · 곧 도착(정차는 문자 없이 웹 타이머만)
    api.completePickup(created.pickupId, CompletePickupRequest(boarded = true, boardedAtEpochMillis = System.currentTimeMillis(), waitedSeconds = 20, pickupLocationChanges = 0, boardingSource = "DOOR"))

    assertEquals("BOARDED", deps.hub.view(token).phase)
    assertEquals(created.pickupId, api.recentPickups(5).first().id)
  }

  @Test
  fun unknownPickup_isAnApiError() = runBlocking {
    val error = runCatching { api.reportArrival("nope", ArrivalRequest(0, 60)) }.exceptionOrNull()
    assertTrue(error is WayApiException && error.code == 404)
  }
}
