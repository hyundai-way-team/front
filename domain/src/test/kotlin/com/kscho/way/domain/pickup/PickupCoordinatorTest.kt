package com.kscho.way.domain.pickup

import com.kscho.way.domain.messaging.PassengerText
import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationSharing
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.RoutePreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PickupCoordinatorTest {
  private val repo = FakePickupRepository()
  private val history = FakePickupHistory()
  private val nav = FakeNavigator()
  private val door = FakeBoardingSensor()
  private val messenger = FakePassengerMessenger()

  private fun TestScope.coordinator() =
    PickupCoordinator(repo, history, nav, door, messenger, backgroundScope, clock = { testScheduler.currentTime })

  private fun TestScope.collectEffects(coordinator: PickupCoordinator): MutableList<PickupEffect> {
    val effects = mutableListOf<PickupEffect>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.effects.toList(effects) }
    return effects
  }

  @Test
  fun start_createsSessionAndGuidesToPickupPoint() = runTest {
    val coordinator = coordinator()
    val state = coordinator.start(Fixtures.plan())
    assertEquals(1, repo.created.size)
    assertEquals(12 * 60_000L, repo.created.single().second) // 지금(0) + 12분
    assertEquals(Fixtures.pickupLocation, nav.guidedTo)
    assertEquals(PickupPhase.DRIVING, state.phase)
    assertTrue(coordinator.hasActivePickup)
  }

  @Test
  fun start_textsJoinLinkFromCar() = runTest {
    val coordinator = coordinator()
    val state = coordinator.start(Fixtures.plan())
    runCurrent()

    // 문자는 서버가 아니라 차(운전자 번호)가 보낸다 — 서버에서 받은 참여 링크를 넣어서
    val (to, text) = messenger.sent.single()
    assertEquals(Fixtures.plan().passenger, to)
    val departure = text as PassengerText.Departure
    assertEquals(state.shareUrl, departure.link)
    assertEquals(state.arrivalAtMillis, departure.arrivalAtMillis)
    assertEquals("김도윤", departure.driverName)
    assertEquals(Fixtures.plan().pickupPoint.description.title, departure.pickupTitle)
  }

  @Test
  fun etaChangeAndArrivingSoon_areTextedFromCar_andToldToServer() = runTest {
    val coordinator = coordinator()
    val state = coordinator.start(Fixtures.plan())
    runCurrent()

    // 정체로 12분 → 20분: 3분 넘게 바뀌어 다시 알린다
    nav.events.emit(NavEvent.Progress(null, 20 * 60, 3_000, 20 * 60_000L))
    runCurrent()
    assertEquals(PassengerText.EtaChanged(state.shareUrl, 12 * 60_000L, 20 * 60_000L), messenger.sent.last().second)
    assertEquals(listOf(12 * 60_000L to 20 * 60_000L), repo.etaChanges) // 서버는 동승자 웹 배너용

    nav.events.emit(NavEvent.Progress(null, 4 * 60, 1_100, 20 * 60_000L))
    runCurrent()
    assertEquals(PassengerText.ArrivingSoon(state.shareUrl), messenger.sent.last().second)
    assertEquals(1, repo.arrivingSoon.size)
    assertEquals(3, messenger.sent.size)
  }

  @Test
  fun eventRightAfterStart_isNotLost() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    // 구독이 출발과 동시에 걸려 있어야 한다
    nav.events.emit(NavEvent.Progress(null, 4 * 60, 1_100, 12 * 60_000L))
    runCurrent()
    assertEquals(listOf(12 * 60_000L), repo.arrivingSoon)
  }

  @Test
  fun wholePickup_textsAnnouncesWaitsOffersCallAndCompletes() = runTest {
    val coordinator = coordinator()
    val effects = collectEffects(coordinator)
    coordinator.start(Fixtures.plan())

    repo.passenger.value = PassengerStatus(linkOpened = true, sharing = LocationSharing.SHARING)
    runCurrent()
    assertTrue(effects.any { it is PickupEffect.Announce && it.announcement is Announcement.LinkChecked })

    nav.events.emit(NavEvent.Progress(null, 4 * 60, 1_100, 12 * 60_000L))
    runCurrent()
    assertEquals(1, repo.arrivingSoon.size)
    assertEquals(listOf(4 * 60), repo.driverReports)

    val arrivedAt = testScheduler.currentTime
    nav.events.emit(NavEvent.Arrived(arrivedAt))
    runCurrent()
    assertEquals(listOf(arrivedAt), repo.arrivals)
    assertEquals(listOf(4 * 60, 0), repo.driverReports) // 정차 때 마지막 위치 한 번 더
    assertEquals(PickupPhase.WAITING, coordinator.state.value?.phase)

    advanceTimeBy(46_000)
    runCurrent()
    assertEquals(1, effects.count { it is PickupEffect.OfferCall })

    door.boardings.emit(testScheduler.currentTime)
    runCurrent()
    val state = coordinator.state.value!!
    assertEquals(PickupPhase.BOARDED, state.phase)
    assertEquals(BoardingSource.DOOR, state.boardingSource)
    assertEquals(1, repo.completed.size)
    // 픽업 안내는 D-11 의 5초가 지나 잇는 순간에 거둔다 — 탑승하자마자 거두면 Pleos 내비가 앞으로 나와 D-11 을 가린다
    assertEquals(0, nav.stopped)
    assertFalse(coordinator.hasActivePickup)
  }

  @Test
  fun boarding_withDestination_waitsForDriver_thenHandsOverOnce() = runTest {
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()

    door.boardings.emit(testScheduler.currentTime)
    runCurrent()
    // D-10: 운전자가 '탑승 완료'를 누르거나 10초 카운트다운이 끝날 때까지 기다린다
    assertEquals(PickupPhase.BOARDED, coordinator.state.value?.phase)
    assertNull(nav.handedOverTo)
    assertEquals(0, nav.stopped)

    assertTrue(coordinator.continueToDestination())
    runCurrent()
    assertEquals(dest.location, nav.handedOverTo)
    assertFalse(coordinator.continueToDestination()) // 두 번 넘기지 않는다
    assertEquals(1, nav.handOvers)
  }

  @Test
  fun boarding_withDestination_handsOverByItselfFiveSecondsLater() = runTest {
    // D-11. 실차에선 Pleos 내비가 앞에 있어 Way 화면이 안 보인다 — 화면 없이도 5초 뒤 넘기고 픽업을 닫는다
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()

    advanceTimeBy(4_999)
    runCurrent()
    assertNull(nav.handedOverTo)

    advanceTimeBy(2)
    runCurrent()
    assertEquals(dest.location, nav.handedOverTo)
    assertEquals(1, nav.handOvers)
    assertNull(coordinator.state.value)
  }

  @Test
  fun boarding_withoutDestination_opensDefaultNaviFiveSecondsLater() = runTest {
    // D-11 목적지 없음: 픽업 안내를 끝내고 5초 뒤 기본 내비로(운전자가 음성으로 목적지를 정하게)
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()
    assertEquals(0, nav.stopped) // 거두면 내비가 앞으로 나와 D-11 을 가린다

    advanceTimeBy(4_999)
    runCurrent()
    assertEquals(0, nav.naviOpened)
    assertEquals(0, nav.stopped)
    assertEquals(PickupPhase.BOARDED, coordinator.state.value?.phase)

    advanceTimeBy(2)
    runCurrent()
    assertEquals(1, nav.stopped) // 픽업 위치 안내를 거두고
    assertEquals(1, nav.naviOpened) // 기본 내비로
    assertEquals(0, nav.handOvers)
    assertNull(coordinator.state.value)
  }

  @Test
  fun connectNow_handsOverAtOnce_andOnlyOnce() = runTest {
    // D-11 '안내 시작'
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()
    advanceTimeBy(1_000)

    coordinator.connectNext()
    runCurrent()
    assertEquals(dest.location, nav.handedOverTo)
    assertNull(coordinator.state.value)
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(1, nav.handOvers)
  }

  @Test
  fun connectNow_withoutDestination_opensNaviAtOnce() = runTest {
    // D-11 '지금 연결'
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()

    coordinator.connectNext()
    runCurrent()
    assertEquals(1, nav.naviOpened)
    assertNull(coordinator.state.value)
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(1, nav.naviOpened)
  }

  @Test
  fun cancel_staysOnBoardedScreen_andClosingThenConnectsNothing() = runTest {
    // D-11 '취소' → D-10 에 남고, '탑승 완료'로 닫아도 목적지 안내·내비 연결을 하지 않는다
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()
    advanceTimeBy(2_000)

    coordinator.cancelNext()
    runCurrent()
    assertTrue(coordinator.state.value?.nextCanceled == true)
    // D-10 처럼 픽업 안내를 끝낸다고 알린다. 안내 자체는 D-10 을 닫을 때 거둔다 — 지금 거두면 Pleos 내비가 앞으로 나와 D-10 을 가린다
    assertEquals("탑승을 확인했어요. 안내를 종료할게요.", coordinator.state.value?.lastAnnouncement?.announcement?.text)
    assertEquals(0, nav.stopped)
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(0, nav.handOvers)
    assertEquals(PickupPhase.BOARDED, coordinator.state.value?.phase)

    coordinator.finish()
    runCurrent()
    assertEquals(1, nav.stopped) // 닫으며 픽업 위치 안내를 거둔다(정차 전에 탔다면 아직 켜져 있다)
    assertEquals(0, nav.handOvers)
    assertEquals(0, nav.naviOpened)
    assertNull(coordinator.state.value)
  }

  @Test
  fun connectAfterCancel_doesNothing() = runTest {
    // 5초 경계에서 '취소'와 타이머가 겹쳐 취소가 먼저 잡혀도, 뒤이은 연결은 하지 않는다
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()

    coordinator.cancelNext()
    coordinator.connectNext()
    runCurrent()
    assertEquals(0, nav.handOvers)
    assertEquals(PickupPhase.BOARDED, coordinator.state.value?.phase)
  }

  @Test
  fun boarding_withDestination_estimatesArrivalThere() = runTest {
    // D-11 '10:05 도착 예정 (약 15분)' — 탑승한 자리에서 목적지까지 내비에 물어 본다
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()
    nav.route = RoutePreview(5_000, 15 * 60)
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()

    val s = coordinator.state.value!!
    assertEquals(dest.location, nav.previewed.last())
    assertEquals(15 * 60, s.destinationTripSeconds)
    assertEquals(testScheduler.currentTime + 15 * 60_000L, s.destinationArrivalAtMillis)
  }

  @Test
  fun finishBeforeCountdown_cancelsAutoHandOver_andLeavesNextPickupAlone() = runTest {
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()
    advanceTimeBy(3_000)
    coordinator.finish() // '탑승 완료'를 먼저 눌렀다
    runCurrent()

    coordinator.start(Fixtures.plan()) // 곧바로 다음 픽업
    runCurrent()
    advanceTimeBy(20_000)
    runCurrent()
    assertEquals(1, nav.handOvers)
    assertTrue(coordinator.hasActivePickup) // 앞 픽업의 10초가 다음 픽업을 닫지 않는다
  }

  @Test
  fun finish_afterBoardingWithPendingDestination_stillHandsOver() = runTest {
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()

    coordinator.finish() // D-10 을 그냥 닫아도 정해 둔 목적지 안내는 넘긴다
    runCurrent()
    assertEquals(dest.location, nav.handedOverTo)
    assertNull(coordinator.state.value)
  }

  @Test
  fun arrival_reportsLastCarPositionRightAway() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    val far = LatLng(37.5570, 126.9450)
    val near = LatLng(37.55671, 126.94205)
    nav.events.emit(NavEvent.Progress(far, 60, 300, 12 * 60_000L))
    runCurrent()
    advanceTimeBy(2_000) // 5초 간격 안 — 이 위치는 보고를 건너뛴다
    nav.events.emit(NavEvent.Progress(near, 3, 8, 12 * 60_000L))
    runCurrent()

    nav.events.emit(NavEvent.Arrived(testScheduler.currentTime))
    runCurrent()

    // 정차하면 서기 직전 위치를 바로 — 동승자 지도의 차가 픽업 지점 앞에서 멈춰 보이지 않게
    assertEquals(listOf(far, near), repo.driverLocations)
    assertEquals(0, repo.driverReports.last())
    assertEquals(1, repo.arrivals.size)
  }

  @Test
  fun arrivalWithoutAnyPosition_reportsPickupPoint() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    nav.events.emit(NavEvent.Arrived(testScheduler.currentTime))
    runCurrent()
    assertEquals(listOf(Fixtures.pickupLocation), repo.driverLocations)
  }

  @Test
  fun boarding_savesRecordInAppNotOnServer() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    advanceTimeBy(30_000)
    coordinator.confirmBoarded(BoardingSource.MANUAL)
    runCurrent()

    // 홈 '최근 픽업'은 앱 기록 — 서버엔 지표(탑승·대기·위치 변경)만 간다
    val record = history.saved.single()
    assertEquals(Fixtures.seoyeon, record.passenger)
    assertEquals(Fixtures.plan().pickupPoint, record.pickupPoint)
    assertTrue(record.boarded)
    assertEquals(testScheduler.currentTime, record.endedAtMillis)
    assertEquals(1, repo.completed.size)
  }

  @Test
  fun manualBoarding_completesWithoutDoor() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    coordinator.confirmBoarded(BoardingSource.MANUAL)
    runCurrent()
    assertEquals(BoardingSource.MANUAL, repo.completed.single().boardingSource)
  }

  @Test
  fun doorAfterBoarding_doesNotCompleteTwice() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    coordinator.confirmBoarded(BoardingSource.MANUAL)
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()
    assertEquals(1, repo.completed.size)
  }

  @Test
  fun finish_clearsStateAndAllowsNextPickup() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    coordinator.confirmBoarded(BoardingSource.MANUAL)
    runCurrent()
    coordinator.finish()
    assertNull(coordinator.state.value)
    coordinator.start(Fixtures.plan())
    assertEquals(2, repo.created.size)
  }

  @Test
  fun navigationFailure_sendsNoTextAndLeavesNoPickup() = runTest {
    val coordinator = coordinator()
    nav.failGuidance = SecurityException("NAVI_ROUTE_SEARCH")
    val error = runCatching { coordinator.start(Fixtures.plan()) }.exceptionOrNull()
    assertTrue(error is SecurityException)
    assertTrue(repo.created.isEmpty()) // 내비를 못 켰으면 동승자에게 문자도 안 보낸다
    assertTrue(messenger.sent.isEmpty())
    assertNull(coordinator.state.value)
    assertFalse(coordinator.hasActivePickup)
    nav.failGuidance = null
    coordinator.start(Fixtures.plan())
    assertEquals(1, repo.created.size)
  }

  @Test
  fun serverFailure_stopsGuidanceAndLeavesNoPickup() = runTest {
    val coordinator = coordinator()
    repo.failCreate = IllegalStateException("서버 오류")
    assertTrue(runCatching { coordinator.start(Fixtures.plan()) }.isFailure)
    runCurrent()
    assertEquals(1, nav.stopped)
    assertTrue(messenger.sent.isEmpty()) // 링크가 없으면 문자도 없다
    assertNull(coordinator.state.value)
  }

  @Test(expected = IllegalStateException::class)
  fun start_whileActive_isRejected() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    coordinator.start(Fixtures.plan())
  }
}
