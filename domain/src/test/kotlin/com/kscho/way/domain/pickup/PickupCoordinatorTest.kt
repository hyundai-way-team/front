package com.kscho.way.domain.pickup

import com.kscho.way.domain.messaging.PassengerText
import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.LocationSharing
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.PassengerStatus
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
  private val nav = FakeNavigator()
  private val door = FakeBoardingSensor()
  private val messenger = FakePassengerMessenger()

  private fun TestScope.coordinator() =
    PickupCoordinator(repo, nav, door, messenger, backgroundScope, clock = { testScheduler.currentTime })

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
    assertEquals(1, nav.stopped)
    assertFalse(coordinator.hasActivePickup)
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
