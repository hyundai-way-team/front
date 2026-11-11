package com.kscho.way.server.pickup

import com.kscho.way.data.mock.MockWorld
import com.kscho.way.data.remote.ArrivalRequest
import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.DriverStatusRequest
import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.NotificationRequest
import com.kscho.way.data.remote.NotificationType
import com.kscho.way.data.remote.SharingWire
import com.kscho.way.data.sms.PickupTexts
import com.kscho.way.server.sms.OutboxTextLog
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupHubTest {
  private fun epoch(text: String) = LocalDateTime.parse(text).atZone(MockWorld.ZONE).toInstant().toEpochMilli()

  private var now = epoch("2026-11-09T09:10:00")
  private val outbox = OutboxTextLog(clock = { now })
  private var nextId = 0
  private val hub = PickupHub(clock = { now }, texts = outbox, linkBase = "http://localhost:8080", newId = { "id${nextId++}" })

  private val pickupPoint = LatLngDto(37.5567, 126.942)

  private val request =
    CreatePickupRequest(
      passengerName = "이서연",
      passengerPhone = "01000001234",
      placeName = "신촌 XX 빌라",
      pickupPoint = pickupPoint,
      pickupTitle = "신촌 XX 빌라 골목 입구 앞",
      pickupDetail = "신촌로 · 도로 북쪽(빌라 쪽) 인도",
      arrivalAtEpochMillis = epoch("2026-11-09T09:50:00"),
      vehicleDescription = "흰색 아반떼",
      plateNumber = "12가 3456",
      driverName = "김도윤",
    )

  private fun created() = hub.create(request)

  private fun tokenOf(shareUrl: String) = shareUrl.substringAfterLast("/p/")

  @Test
  fun create_givesJoinLink_andKeepsCopyOfCarText() {
    val response = created()

    // 문자는 차가 보낸다 — 서버는 같은 문구를 사본으로만
    val copy = outbox.messages.value.single()
    assertEquals("01000001234", copy.to)
    assertEquals(response.pickupId, copy.pickupId)
    assertTrue(response.shareUrl.startsWith("http://localhost:8080/p/"))
    assertEquals(PickupTexts.departure("김도윤", "흰색 아반떼", "12가 3456", "신촌 XX 빌라 골목 입구 앞", "09:50", response.shareUrl), copy.text)
  }

  @Test
  fun linkTokenIsNotThePickupId() {
    val response = created()
    // 동승자 링크로 운전자 쪽 API(pickupId)를 부를 수 없게 서로 다른 값
    assertFalse(tokenOf(response.shareUrl) == response.pickupId)
  }

  @Test
  fun passengerOpensSharesAndMoves_driverSeesIt() {
    val response = created()
    val token = tokenOf(response.shareUrl)

    hub.openLink(token)
    hub.setSharing(token, allow = true)
    hub.updateLocation(token, LatLngDto(37.5571, 126.942))

    val status = hub.passengerStatus(response.pickupId)
    assertTrue(status.linkOpened)
    assertEquals(SharingWire.SHARING, status.sharing)
    assertEquals(LatLngDto(37.5571, 126.942), status.location)
  }

  @Test
  fun declined_driverSeesNotShared_andLocationIsRejected() {
    val response = created()
    val token = tokenOf(response.shareUrl)
    hub.openLink(token)
    hub.setSharing(token, allow = false)

    assertEquals(SharingWire.DECLINED, hub.passengerStatus(response.pickupId).sharing)
    assertTrue(runCatching { hub.updateLocation(token, pickupPoint) }.exceptionOrNull() is PickupError.NotSharing)
    assertNull(hub.passengerStatus(response.pickupId).location)
  }

  @Test
  fun passengerSaysLate_driverSeesItOnce_andWebShowsIt() {
    val response = created()
    val token = tokenOf(response.shareUrl)
    hub.openLink(token)
    hub.markLate(token)
    hub.markLate(token)

    assertTrue(hub.passengerStatus(response.pickupId).late)
    assertTrue(hub.view(token).late)
  }

  @Test
  fun lateAfterBoarding_isRejected() {
    val response = created()
    val token = tokenOf(response.shareUrl)
    hub.complete(response.pickupId, CompletePickupRequest(boarded = true, pickupLocationChanges = 0))
    assertTrue(runCatching { hub.markLate(token) }.exceptionOrNull() is PickupError.LinkExpired)
  }

  @Test
  fun driverReport_showsCarOnPassengerView() {
    val response = created()
    now += 60_000
    hub.reportDriver(response.pickupId, DriverStatusRequest(LatLngDto(37.559, 126.96), 1800, 7000, epoch("2026-11-09T09:41:00")))

    val view = hub.view(tokenOf(response.shareUrl))
    assertEquals(LatLngDto(37.559, 126.96), view.car?.location)
    assertEquals(1800, view.car?.remainingSeconds)
    assertEquals(epoch("2026-11-09T09:41:00"), view.arrivalAt)
    assertEquals(Phase.EN_ROUTE.name, view.phase)
  }

  @Test
  fun etaChanged_textsBeforeAndAfter_andShowsBanner() {
    val response = created()
    now = epoch("2026-11-09T09:38:00")
    hub.notify(
      response.pickupId,
      NotificationRequest(NotificationType.ETA_CHANGED, epoch("2026-11-09T09:55:00"), epoch("2026-11-09T09:50:00")),
    )

    assertEquals(PickupTexts.etaChanged("09:50", "09:55", response.shareUrl), outbox.messages.value.last().text)
    val view = hub.view(tokenOf(response.shareUrl))
    assertEquals(epoch("2026-11-09T09:50:00"), view.etaChange?.from)
    assertEquals(epoch("2026-11-09T09:55:00"), view.etaChange?.to)
    assertEquals(epoch("2026-11-09T09:55:00"), view.arrivalAt)
  }

  @Test
  fun arrivingSoon_textsOnce_andMovesPhase() {
    val response = created()
    val soon = NotificationRequest(NotificationType.ARRIVING_SOON, epoch("2026-11-09T09:55:00"))
    hub.notify(response.pickupId, soon)
    hub.notify(response.pickupId, soon)

    assertEquals(2, outbox.messages.value.size) // 출발 + 곧 도착 1번
    assertEquals(PickupTexts.arrivingSoon(response.shareUrl), outbox.messages.value.last().text)
    assertEquals(Phase.ARRIVING_SOON.name, hub.view(tokenOf(response.shareUrl)).phase)
  }

  @Test
  fun arrival_startsSameOneMinuteTimerOnServerClock() {
    val response = created()
    now = epoch("2026-11-09T09:55:00")
    // 운전자 앱 시계가 서버와 조금 달라도 동승자 웹 타이머는 서버 시계로 맞춘다
    hub.arrival(response.pickupId, ArrivalRequest(arrivedAtEpochMillis = now - 3_000, waitSeconds = 60))

    val view = hub.view(tokenOf(response.shareUrl))
    assertEquals(Phase.ARRIVED.name, view.phase)
    assertEquals(now, view.timer?.startedAt)
    assertEquals(now + 60_000, view.timer?.endsAt)
  }

  @Test
  fun complete_endsSharingExpiresLinkAndRecords() {
    val response = created()
    val token = tokenOf(response.shareUrl)
    hub.openLink(token)
    hub.setSharing(token, allow = true)
    hub.updateLocation(token, pickupPoint)

    hub.complete(response.pickupId, CompletePickupRequest(boarded = true, boardedAtEpochMillis = now, waitedSeconds = 30, pickupLocationChanges = 0, boardingSource = "DOOR"))

    val status = hub.passengerStatus(response.pickupId)
    assertEquals(SharingWire.ENDED, status.sharing)
    assertNull(status.location)
    assertEquals(Phase.BOARDED.name, hub.view(token).phase)
    assertNull(hub.view(token).me)
    assertTrue(runCatching { hub.updateLocation(token, pickupPoint) }.exceptionOrNull() is PickupError.LinkExpired)
    val record = hub.recent(5).first()
    assertEquals("신촌 XX 빌라 골목 입구 앞", record.pickupTitle)
    assertTrue(record.boarded)
    assertEquals(30, record.waitedSeconds)
  }

  @Test
  fun unknownLinkOrPickup_isNotFound() {
    assertTrue(runCatching { hub.view("nope") }.exceptionOrNull() is PickupError.NotFound)
    assertTrue(runCatching { hub.reportDriver("nope", DriverStatusRequest(null, 0, 0, 0)) }.exceptionOrNull() is PickupError.NotFound)
  }

  @Test
  fun passengerView_usesCallNames() {
    val view = hub.view(tokenOf(created().shareUrl))
    assertEquals("도윤", view.driverCallName)
    assertEquals("서연", view.passengerCallName)
    assertEquals("12가 3456", view.vehicle.plateNumber)
    assertEquals("신촌 XX 빌라 골목 입구 앞", view.pickup.title)
  }
}
