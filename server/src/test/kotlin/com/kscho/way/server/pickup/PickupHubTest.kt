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
import com.kscho.way.data.remote.WalkRouteDto
import com.kscho.way.data.sms.PickupTexts
import com.kscho.way.server.sms.OutboxTextLog
import com.kscho.way.server.walk.WalkAnswer
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
      passengerCallName = "서연",
      placeName = "신촌 XX 빌라",
      pickupPoint = pickupPoint,
      pickupTitle = "신촌 XX 빌라 골목 입구 앞",
      pickupDetail = "신촌로 · 도로 북쪽(빌라 쪽) 인도",
      arrivalAtEpochMillis = epoch("2026-11-09T09:50:00"),
      vehicleDescription = "흰색 아반떼",
      plateNumber = "12가 3456",
      driverName = "김도윤",
      driverCallName = "도윤",
    )

  private fun created() = hub.create(request)

  private fun tokenOf(shareUrl: String) = shareUrl.substringAfterLast("/p/")

  @Test
  fun create_givesJoinLink_andKeepsCopyOfCarText() {
    val response = created()

    // 문자는 차가 보낸다 — 서버는 같은 문구를 사본으로만
    val copy = outbox.messages.value.single()
    assertEquals("이서연", copy.to) // 서버엔 동승자 번호가 없다
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

  /** 걷는 길은 뒤에서 묻는다 — 시험에선 바로 돌게(Unconfined). */
  private fun routed(walkRoute: suspend (LatLngDto, LatLngDto) -> WalkAnswer) =
    PickupHub(
      clock = { now },
      texts = outbox,
      linkBase = "http://x",
      newId = { "id${nextId++}" },
      walkRoute = walkRoute,
      scope = CoroutineScope(Dispatchers.Unconfined),
    )

  @Test
  fun sharingPassenger_getsAWalkingRoute_sameForDriverAndWeb() {
    // P-04a · P-05 '이 경로를 따라 이동해 주세요' — 운전자 화면(D-08)에도 같은 경로
    val asked = mutableListOf<Pair<LatLngDto, LatLngDto>>()
    val hub = routed { from, to ->
      asked += from to to
      WalkAnswer.Found(WalkRouteDto(listOf(from, to), meters = 120, seconds = 107))
    }
    val response = hub.create(request)
    val token = tokenOf(response.shareUrl)
    hub.setSharing(token, allow = true)
    val here = LatLngDto(37.5577, 126.942)
    hub.updateLocation(token, here)

    assertEquals(listOf(here to pickupPoint), asked)
    val walk = hub.view(token).walk!!
    assertEquals(120, walk.meters)
    assertEquals(walk, hub.passengerStatus(response.pickupId).walk)

    // 몇 m 안 움직이면 다시 묻지 않는다(위치는 1초마다 온다) — 경로 시작만 지금 자리로
    val step = LatLngDto(37.55772, 126.942)
    hub.updateLocation(token, step)
    assertEquals(1, asked.size)
    assertEquals(step, hub.view(token).walk!!.path.first())
    hub.updateLocation(token, LatLngDto(37.5575, 126.942)) // 약 22 m
    assertEquals(2, asked.size)

    // 탑승하면 경로도 끝
    hub.complete(response.pickupId, CompletePickupRequest(boarded = true, boardedAtEpochMillis = now, waitedSeconds = 10, pickupLocationChanges = 0, boardingSource = "DOOR"))
    assertNull(hub.view(token).walk)
  }

  @Test
  fun noRoute_isRemembered_soTheFreeServiceIsNotAskedEverySecond() {
    var asked = 0
    val hub = routed { _, _ ->
      asked++
      WalkAnswer.NoRoute
    }
    val token = tokenOf(hub.create(request).shareUrl)
    hub.setSharing(token, allow = true)
    hub.updateLocation(token, LatLngDto(37.5577, 126.942))
    hub.updateLocation(token, LatLngDto(37.55772, 126.942))
    assertEquals(1, asked)
    // 웹은 '경로 안내'·'경로를 찾고 있어요'를 거둔다
    assertTrue(hub.view(token).walkFailed)
    assertNull(hub.view(token).walk)
  }

  @Test
  fun farAway_isNotAsked() {
    // 3 km 넘게 떨어진 동승자(아직 집·지하철)는 걷는 안내가 아니라 묻지 않는다
    var asked = 0
    val hub = routed { _, _ ->
      asked++
      WalkAnswer.NoRoute
    }
    val token = tokenOf(hub.create(request).shareUrl)
    hub.setSharing(token, allow = true)
    hub.updateLocation(token, LatLngDto(37.60, 126.942))
    assertEquals(0, asked)
    assertTrue(hub.view(token).walkFailed)
  }

  @Test
  fun serviceBusy_keepsTheLastRoute_andAsksAgainLater() {
    // 공개 경로 서버가 잠깐 바쁘거나 늦으면(429·5xx·시간 초과) '길 없음'으로 굳히지 않는다 — 찾아 둔 길은 그대로, 20초 뒤 다시
    var answer: WalkAnswer = WalkAnswer.Found(WalkRouteDto(listOf(pickupPoint, pickupPoint), 120, 107))
    var asked = 0
    val hub = routed { _, _ ->
      asked++
      answer
    }
    val token = tokenOf(hub.create(request).shareUrl)
    hub.setSharing(token, allow = true)
    hub.updateLocation(token, LatLngDto(37.5577, 126.942))
    answer = WalkAnswer.Unavailable
    hub.updateLocation(token, LatLngDto(37.5575, 126.942)) // 22 m — 다시 묻지만 바쁨
    assertEquals(2, asked)
    assertEquals(120, hub.view(token).walk!!.meters)
    assertFalse(hub.view(token).walkFailed)

    // 움직이지 않아도 잠시 뒤 다시 묻는다(가만히 기다리는 동승자)
    hub.updateLocation(token, LatLngDto(37.55751, 126.942))
    assertEquals(2, asked) // 아직 20초 안
    now += PickupHub.WALK_RETRY_MILLIS
    answer = WalkAnswer.Found(WalkRouteDto(listOf(pickupPoint, pickupPoint), 100, 90))
    hub.updateLocation(token, LatLngDto(37.55751, 126.942))
    assertEquals(3, asked)
    assertEquals(100, hub.view(token).walk!!.meters)
  }

  /** 차 길도 뒤에서 묻는다 — 시험에선 바로 돌게(Unconfined). */
  private fun carRouted(carRoute: suspend (LatLngDto, LatLngDto) -> WalkAnswer) =
    PickupHub(
      clock = { now },
      texts = outbox,
      linkBase = "http://x",
      newId = { "id${nextId++}" },
      carRoute = carRoute,
      scope = CoroutineScope(Dispatchers.Unconfined),
    )

  private fun report(hub: PickupHub, pickupId: String, at: LatLngDto) =
    hub.reportDriver(pickupId, DriverStatusRequest(at, 600, 1500, epoch("2026-11-09T09:40:00")))

  // 차가 남쪽에서 와 한 번 살짝 꺾여(동쪽 약 44 m) 픽업 위치(37.5567, 126.942)에 닿는 길
  private val carStart = LatLngDto(37.5500, 126.942)
  private val carMid = LatLngDto(37.5533, 126.9425)

  @Test
  fun driverReport_getsACarRoute_trimmedToTheCar_forTheWeb() {
    // 동승자 웹도 차가 오는 길을 파란 선으로 — 차가 지나온 부분은 잘라서 보낸다
    val asked = mutableListOf<Pair<LatLngDto, LatLngDto>>()
    val hub = carRouted { from, to ->
      asked += from to to
      WalkAnswer.Found(WalkRouteDto(listOf(from, carMid, to), meters = 750, seconds = 120))
    }
    val response = hub.create(request)
    val token = tokenOf(response.shareUrl)
    report(hub, response.pickupId, carStart)

    assertEquals(listOf(carStart to pickupPoint), asked)
    assertEquals(listOf(carStart, carMid, pickupPoint), hub.view(token).carRoute)

    // 길 위를 달리면 다시 묻지 않고, 지나온 점은 빠진다
    val onRoute = LatLngDto(37.5540, 126.942)
    report(hub, response.pickupId, onRoute)
    assertEquals(1, asked.size)
    assertEquals(listOf(onRoute, pickupPoint), hub.view(token).carRoute)
  }

  @Test
  fun offRoute_hidesTheStaleRoute_andAsksAgain_butNotTooOften() {
    var asked = 0
    val hub = carRouted { from, to ->
      asked++
      WalkAnswer.Found(WalkRouteDto(listOf(from, to), 750, 120))
    }
    val response = hub.create(request)
    val token = tokenOf(response.shareUrl)
    report(hub, response.pickupId, carStart)
    now += 5_000
    val detour = LatLngDto(37.5520, 126.9435) // 길에서 약 130 m 옆
    report(hub, response.pickupId, detour)
    assertEquals(1, asked) // 방금 물었다(간격 안)
    // 벗어난 차에서 옛 길로 곧은 선을 긋지 않는다 — 새 길을 받을 때까지 감춘다
    assertNull(hub.view(token).carRoute)
    now += PickupHub.CAR_ROUTE_MIN_INTERVAL_MILLIS
    report(hub, response.pickupId, detour)
    assertEquals(2, asked)
    assertEquals(listOf(detour, pickupPoint), hub.view(token).carRoute)
  }

  @Test
  fun carRoute_serviceBusy_retriesLater_andNoRouteWaitsUntilTheCarMovesFar() {
    var answer: WalkAnswer = WalkAnswer.Unavailable
    var asked = 0
    val hub = carRouted { _, _ ->
      asked++
      answer
    }
    val response = hub.create(request)
    val token = tokenOf(response.shareUrl)
    report(hub, response.pickupId, carStart)
    report(hub, response.pickupId, carStart)
    assertEquals(1, asked)
    assertNull(hub.view(token).carRoute)
    now += PickupHub.CAR_ROUTE_MIN_INTERVAL_MILLIS
    answer = WalkAnswer.NoRoute
    report(hub, response.pickupId, carStart)
    assertEquals(2, asked)
    // 길 없음은 기억 — 조금 움직여선 안 묻고, 멀리 움직이면 다시
    now += PickupHub.CAR_ROUTE_MIN_INTERVAL_MILLIS
    report(hub, response.pickupId, LatLngDto(37.5505, 126.942))
    assertEquals(2, asked)
    report(hub, response.pickupId, carMid)
    assertEquals(3, asked)
  }

  @Test
  fun carArrived_dropsTheCarRoute() {
    val hub = carRouted { from, to -> WalkAnswer.Found(WalkRouteDto(listOf(from, to), 750, 120)) }
    val response = hub.create(request)
    val token = tokenOf(response.shareUrl)
    report(hub, response.pickupId, carStart)
    hub.arrival(response.pickupId, ArrivalRequest(now, 60))
    assertNull(hub.view(token).carRoute)
  }

  @Test
  fun stoppingSharing_dropsTheRoute() {
    val hub = routed { from, to -> WalkAnswer.Found(WalkRouteDto(listOf(from, to), 50, 45)) }
    val response = hub.create(request)
    val token = tokenOf(response.shareUrl)
    hub.setSharing(token, allow = true)
    hub.updateLocation(token, LatLngDto(37.5571, 126.942))
    hub.setSharing(token, allow = false)
    assertNull(hub.view(token).walk)
    assertNull(hub.passengerStatus(response.pickupId).walk)
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
  fun complete_endsSharingAndExpiresLink() {
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
  }

  @Test
  fun unknownLinkOrPickup_isNotFound() {
    assertTrue(runCatching { hub.view("nope") }.exceptionOrNull() is PickupError.NotFound)
    assertTrue(runCatching { hub.reportDriver("nope", DriverStatusRequest(null, 0, 0, 0)) }.exceptionOrNull() is PickupError.NotFound)
  }

  @Test
  fun passengerView_usesCallNamesSentByApp() {
    // 호칭은 앱이 정해 보낸다 — 서버는 이름 규칙 없이 받은 그대로
    val view = hub.view(tokenOf(hub.create(request.copy(passengerCallName = "서연이", driverCallName = "도윤 기사")).shareUrl))
    assertEquals("도윤 기사", view.driverCallName)
    assertEquals("서연이", view.passengerCallName)
    assertEquals("김도윤", view.driverName)
    assertEquals("12가 3456", view.vehicle.plateNumber)
    assertEquals("신촌 XX 빌라 골목 입구 앞", view.pickup.title)
  }

  // ── 공개 서버: 인증 없는 픽업 만들기가 메모리를 끝없이 채우지 않게

  @Test
  fun create_dropsPickupsOlderThanRetention() {
    val small = PickupHub(clock = { now }, texts = OutboxTextLog(clock = { now }), linkBase = "http://x", newId = { "id${nextId++}" }, retentionMillis = 60_000, maxPickups = 10)
    val old = small.create(request)
    now += 61_000
    small.create(request)
    assertThrows(PickupError.NotFound::class.java) { small.view(old.shareUrl.substringAfterLast("/p/")) }
  }

  @Test
  fun create_refusesWhenFull() {
    val small = PickupHub(clock = { now }, texts = OutboxTextLog(clock = { now }), linkBase = "http://x", newId = { "id${nextId++}" }, retentionMillis = 60_000, maxPickups = 2)
    small.create(request)
    small.create(request)
    assertThrows(PickupError.Busy::class.java) { small.create(request) }
  }

  @Test
  fun outbox_keepsOnlyRecentCopies() {
    val log = OutboxTextLog(clock = { now }, maxMessages = 3)
    repeat(5) { log.record("pk_$it", "이서연", "문자 $it") }
    assertEquals(listOf("문자 2", "문자 3", "문자 4"), log.messages.value.map { it.text })
  }
}
