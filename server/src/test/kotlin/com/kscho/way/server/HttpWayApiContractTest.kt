package com.kscho.way.server

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.mock.MockWorld
import com.kscho.way.data.remote.HttpWayApi
import com.kscho.way.data.remote.WayApiException
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.request.NotificationType
import com.kscho.way.data.response.SharingWire
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
      passengerCallName = "서연",
      placeName = "신촌 XX 빌라",
      pickupPoint = LatLngDto(MockWorld.VILLA_ALLEY_ENTRANCE.latitude, MockWorld.VILLA_ALLEY_ENTRANCE.longitude),
      pickupTitle = "신촌 XX 빌라 골목 입구 앞",
      pickupDetail = "신촌로 · 도로 북쪽(빌라 쪽) 인도",
      arrivalAtEpochMillis = System.currentTimeMillis() + 600_000,
      vehicleDescription = "흰색 아반떼",
      plateNumber = "12가 3456",
      driverName = "김도윤",
      driverCallName = "도윤",
    )

  @Test
  fun placesAndZoneData() = runBlocking {
    assertEquals("신촌 XX 빌라", api.searchPlaces("xx").first().name)
    val here = LatLngDto(MockWorld.VILLA_ALLEY_ENTRANCE.latitude, MockWorld.VILLA_ALLEY_ENTRANCE.longitude)
    assertEquals("신촌 XX 빌라 골목 입구 앞", api.describeLocation(here).title)
    // 정차 금지 구간은 데이터로만 — 실제 버스정류장 좌표(국토교통부 공공데이터) 둘레 10 m. 그리기·판단은 앱
    val zones = api.stopZones(here, 300)
    assertTrue(zones.isNotEmpty() && zones.all { it.id.startsWith("bus-") && it.path.size == 1 && it.radiusMeters == 10.0 })
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
    assertEquals(SharingWire.ENDED, deps.hub.passengerStatus(created.pickupId).sharing)
  }

  @Test
  fun unknownPickup_isAnApiError() = runBlocking {
    val error = runCatching { api.reportArrival("nope", ArrivalRequest(0, 60)) }.exceptionOrNull()
    assertTrue(error is WayApiException && error.code == 404)
  }
}
