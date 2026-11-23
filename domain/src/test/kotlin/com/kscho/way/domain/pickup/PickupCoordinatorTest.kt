package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.message.PassengerText
import com.kscho.way.domain.model.passenger.LocationSharing
import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.route.NavEvent
import com.kscho.way.domain.model.route.RoutePreview
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
    assertEquals(12 * 60_000L, repo.created.single().second)
    assertEquals(Fixtures.pickupLocation, nav.guidedTo)
    assertEquals(PickupPhase.DRIVING, state.phase)
    assertTrue(coordinator.hasActivePickup)
  }

  @Test
  fun start_textsJoinLinkFromCar() = runTest {
    val coordinator = coordinator()
    val state = coordinator.start(Fixtures.plan())
    runCurrent()

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

    nav.events.emit(NavEvent.Progress(null, 20 * 60, 3_000, 20 * 60_000L))
    runCurrent()
    assertEquals(PassengerText.EtaChanged(state.shareUrl, 12 * 60_000L, 20 * 60_000L), messenger.sent.last().second)
    assertEquals(listOf(12 * 60_000L to 20 * 60_000L), repo.etaChanges)

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
    assertEquals(listOf(4 * 60, 0), repo.driverReports)
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
    assertEquals(PickupPhase.BOARDED, coordinator.state.value?.phase)
    assertNull(nav.handedOverTo)
    assertEquals(0, nav.stopped)

    assertTrue(coordinator.continueToDestination())
    runCurrent()
    assertEquals(dest.location, nav.handedOverTo)
    assertFalse(coordinator.continueToDestination())
    assertEquals(1, nav.handOvers)
  }

  @Test
  fun boarding_withDestination_handsOverByItselfFiveSecondsLater() = runTest {
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
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()
    assertEquals(0, nav.stopped)

    advanceTimeBy(4_999)
    runCurrent()
    assertEquals(0, nav.naviOpened)
    assertEquals(0, nav.stopped)
    assertEquals(PickupPhase.BOARDED, coordinator.state.value?.phase)

    advanceTimeBy(2)
    runCurrent()
    assertEquals(1, nav.stopped)
    assertEquals(1, nav.naviOpened)
    assertEquals(0, nav.handOvers)
    assertNull(coordinator.state.value)
  }

  @Test
  fun connectNow_handsOverAtOnce_andOnlyOnce() = runTest {
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
    assertEquals("탑승을 확인했어요. 안내를 종료할게요.", coordinator.state.value?.lastAnnouncement?.announcement?.text)
    assertEquals(0, nav.stopped)
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(0, nav.handOvers)
    assertEquals(PickupPhase.BOARDED, coordinator.state.value?.phase)

    coordinator.finish()
    runCurrent()
    assertEquals(1, nav.stopped)
    assertEquals(0, nav.handOvers)
    assertEquals(0, nav.naviOpened)
    assertNull(coordinator.state.value)
  }

  @Test
  fun connectAfterCancel_doesNothing() = runTest {
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
    coordinator.finish()
    runCurrent()

    coordinator.start(Fixtures.plan())
    runCurrent()
    advanceTimeBy(20_000)
    runCurrent()
    assertEquals(1, nav.handOvers)
    assertTrue(coordinator.hasActivePickup)
  }

  @Test
  fun finish_afterBoardingWithPendingDestination_stillHandsOver() = runTest {
    val coordinator = coordinator()
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    coordinator.start(Fixtures.plan().copy(destination = dest))
    runCurrent()
    door.boardings.emit(testScheduler.currentTime)
    runCurrent()

    coordinator.finish()
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
    advanceTimeBy(2_000)
    nav.events.emit(NavEvent.Progress(near, 3, 8, 12 * 60_000L))
    runCurrent()

    nav.events.emit(NavEvent.Arrived(testScheduler.currentTime))
    runCurrent()

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
    assertTrue(repo.created.isEmpty())
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
    assertTrue(messenger.sent.isEmpty())
    assertNull(coordinator.state.value)
  }

  @Test(expected = IllegalStateException::class)
  fun start_whileActive_isRejected() = runTest {
    val coordinator = coordinator()
    coordinator.start(Fixtures.plan())
    coordinator.start(Fixtures.plan())
  }
}
