package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationSharing
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.TurnInstruction
import com.kscho.way.domain.model.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupRulesTest {
  private val start = Fixtures.state() // 0 출발, 12분 뒤 도착 예정

  private fun progress(remainingSeconds: Int, arrivalAt: Long = 12 * 60_000L) =
    PickupEvent.Nav(NavEvent.Progress(location = null, remainingSeconds = remainingSeconds, remainingMeters = remainingSeconds * 5, arrivalAtMillis = arrivalAt))

  private fun reduceAll(state: PickupState, vararg steps: Pair<PickupEvent, Long>): Pair<PickupState, List<PickupEffect>> {
    var s = state
    val all = mutableListOf<PickupEffect>()
    for ((event, now) in steps) {
      val t = PickupRules.reduce(s, event, now)
      s = t.state
      all += t.effects
    }
    return s to all
  }

  // ── 3-6 도착 5분 전

  @Test
  fun arrivingSoon_sentOnceWhenFiveMinutesLeft() {
    val (state, effects) =
      reduceAll(start, progress(6 * 60) to 1_000L, progress(5 * 60) to 2_000L, progress(4 * 60) to 3_000L)
    assertTrue(state.arrivingSoonSent)
    assertEquals(1, effects.count { it is PickupEffect.NotifyArrivingSoon })
    val said = effects.filterIsInstance<PickupEffect.Announce>().single().announcement
    assertEquals("서연 님에게 곧 도착한다고 알렸어요", said.text)
  }

  @Test
  fun arrivingSoon_notSentWhileFarAway() {
    val (state, effects) = reduceAll(start, progress(11 * 60) to 1_000L)
    assertFalse(state.arrivingSoonSent)
    assertTrue(effects.isEmpty())
  }

  // ── 3-5 ETA 변경

  @Test
  fun etaChange_resentOnlyWhenMovedByThreshold() {
    val planned = 12 * 60_000L
    val small = planned + 2 * 60_000L
    val big = planned + 4 * 60_000L
    val (state, effects) = reduceAll(start, progress(600, small) to 1_000L, progress(600, big) to 2_000L)
    val notice = effects.filterIsInstance<PickupEffect.NotifyEtaChanged>().single()
    assertEquals(planned, notice.previousArrivalAtMillis)
    assertEquals(big, notice.arrivalAtMillis)
    assertEquals(big, state.notifiedArrivalAtMillis)
    assertEquals(1, state.etaChangeNotices)
  }

  @Test
  fun etaChange_comparesWithLastNotifiedEta() {
    val planned = 12 * 60_000L
    val later = planned + 4 * 60_000L
    val back = planned + 2 * 60_000L // 마지막으로 알린 later 와 2분 차이 → 안 보냄
    val (_, effects) = reduceAll(start, progress(700, later) to 1_000L, progress(600, back) to 2_000L)
    assertEquals(1, effects.count { it is PickupEffect.NotifyEtaChanged })
  }

  // ── 3-8 링크 확인 안내

  @Test
  fun linkChecked_announcedOnFirstOpenOnly() {
    val opened = PickupEvent.Passenger(PassengerStatus(linkOpened = true, sharing = LocationSharing.SHARING))
    val (state, effects) = reduceAll(start, opened to 1_000L, opened to 2_000L)
    assertEquals(LocationSharing.SHARING, state.passenger.sharing)
    val said = effects.filterIsInstance<PickupEffect.Announce>().single().announcement
    assertEquals("서연 님이 픽업 위치를 확인했어요", said.text)
    assertEquals(said, state.lastAnnouncement?.announcement)
  }

  // ── 동승자 '늦어요'(와이어프레임 v2 P-03)

  @Test
  fun passengerLate_announcedOnceToDriver() {
    val opened = PickupEvent.Passenger(PassengerStatus(linkOpened = true, sharing = LocationSharing.SHARING))
    val late = PickupEvent.Passenger(PassengerStatus(linkOpened = true, sharing = LocationSharing.SHARING, late = true))
    val (state, effects) = reduceAll(start, opened to 1_000L, late to 2_000L, late to 3_000L)
    assertTrue(state.passenger.late)
    val said = effects.filterIsInstance<PickupEffect.Announce>().map { it.announcement.text }
    assertEquals(listOf("서연 님이 픽업 위치를 확인했어요", "서연 님이 조금 늦는대요"), said)
  }

  @Test
  fun linkOpenedAndLateTogether_bothAnnounced() {
    val both = PickupEvent.Passenger(PassengerStatus(linkOpened = true, late = true))
    val (_, effects) = reduceAll(start, both to 1_000L)
    assertEquals(2, effects.count { it is PickupEffect.Announce })
  }

  // ── 3-9 정차 · 대기 타이머 · 전화 연결 제안

  @Test
  fun arrival_startsWaitTimerAndReportsIt() {
    val (state, effects) = reduceAll(start, PickupEvent.Nav(NavEvent.Arrived(100_000L)) to 100_000L)
    assertEquals(PickupPhase.WAITING, state.phase)
    assertEquals(PickupEffect.ReportArrived(100_000L), effects.single())
    assertEquals(42_000L, state.waitRemainingMillis(118_000L))
    assertEquals(0L, state.waitRemainingMillis(500_000L))
  }

  @Test
  fun callOffer_onceWhenFifteenSecondsLeft() {
    val arrivedAt = 100_000L
    val (state, effects) =
      reduceAll(
        start,
        PickupEvent.Nav(NavEvent.Arrived(arrivedAt)) to arrivedAt,
        PickupEvent.Tick(arrivedAt + 44_500L) to arrivedAt + 44_500L,
        PickupEvent.Tick(arrivedAt + 45_000L) to arrivedAt + 45_000L,
        PickupEvent.Tick(arrivedAt + 50_000L) to arrivedAt + 50_000L,
      )
    assertTrue(state.callOffered)
    val offer = effects.filterIsInstance<PickupEffect.OfferCall>().single()
    assertEquals("서연 님이 아직 도착하지 않았어요. 전화를 걸까요?", offer.announcement.text)
  }

  @Test
  fun callOffer_neverWhileDriving() {
    val (state, effects) = reduceAll(start, PickupEvent.Tick(10 * 60_000L) to 10 * 60_000L)
    assertFalse(state.callOffered)
    assertTrue(effects.isEmpty())
  }

  @Test
  fun progressAfterArrival_isIgnored() {
    val (state, effects) =
      reduceAll(start, PickupEvent.Nav(NavEvent.Arrived(100_000L)) to 100_000L, progress(4 * 60) to 101_000L)
    assertEquals(0, state.remainingSeconds)
    assertFalse(state.arrivingSoonSent)
    assertEquals(1, effects.size)
  }

  // ── 3-10 탑승 확인 · 종료

  @Test
  fun boardingWhileWaiting_completesWithWaitedSeconds() {
    val (state, effects) =
      reduceAll(
        start,
        PickupEvent.Nav(NavEvent.Arrived(100_000L)) to 100_000L,
        PickupEvent.Boarded(138_000L, BoardingSource.DOOR) to 138_000L,
      )
    assertEquals(PickupPhase.BOARDED, state.phase)
    assertEquals(38, state.waitedSeconds)
    val outcome = effects.filterIsInstance<PickupEffect.Complete>().single().outcome
    assertTrue(outcome.boarded)
    assertEquals(38, outcome.waitedSeconds)
    assertEquals(0, outcome.pickupLocationChanges)
    assertEquals(BoardingSource.DOOR, outcome.boardingSource)
    assertEquals("탑승을 확인했어요. 안내를 종료할게요", effects.filterIsInstance<PickupEffect.Announce>().single().announcement.text)
  }

  @Test
  fun boarding_withDestination_saysDestinationGuidanceFollows() {
    // D-10: 목적지를 정해 뒀으면 '안내 종료'가 아니라 곧 목적지 안내로 넘어간다고 말한다
    val dest = Place("p-dest", "연남동 00 식당", "서울 마포구 연남동", LatLng(37.5620, 126.9250))
    val withDestination = start.copy(plan = start.plan.copy(destination = dest))
    val (state, effects) = reduceAll(withDestination, PickupEvent.Boarded(60_000L, BoardingSource.DOOR) to 60_000L)
    val said = "탑승을 확인했어요. 10초 뒤 목적지 안내를 시작할게요"
    assertEquals(said, effects.filterIsInstance<PickupEffect.Announce>().single().announcement.text)
    assertEquals(said, state.lastAnnouncement?.announcement?.text) // 화면 음성 배너도 같은 말
  }

  @Test
  fun boardingBeforeArrival_alsoCompletes() {
    val (state, effects) = reduceAll(start, PickupEvent.Boarded(60_000L, BoardingSource.DOOR) to 60_000L)
    assertEquals(PickupPhase.BOARDED, state.phase)
    assertNull(effects.filterIsInstance<PickupEffect.Complete>().single().outcome.waitedSeconds)
  }

  @Test
  fun afterBoarding_nothingChanges() {
    val (boarded, _) = reduceAll(start, PickupEvent.Boarded(60_000L, BoardingSource.MANUAL) to 60_000L)
    val (state, effects) =
      reduceAll(
        boarded,
        PickupEvent.Boarded(70_000L, BoardingSource.DOOR) to 70_000L,
        PickupEvent.Nav(NavEvent.Arrived(80_000L)) to 80_000L,
        PickupEvent.Passenger(PassengerStatus(linkOpened = true)) to 90_000L,
      )
    assertEquals(boarded, state)
    assertTrue(effects.isEmpty())
  }

  @Test
  fun turn_isKeptWhileDriving_andDroppedAfterArrival() {
    val turn = TurnInstruction(TurnKind.RIGHT, 300, "성산로")
    val driving = PickupRules.reduce(Fixtures.state(), PickupEvent.Nav(NavEvent.Turn(turn)), 1_000L).state
    assertEquals(turn, driving.nextTurn)
    val stopped = PickupRules.reduce(driving, PickupEvent.Nav(NavEvent.Arrived(2_000L)), 2_000L).state
    assertEquals(null, stopped.nextTurn) // 정차 뒤엔 회전 안내를 지운다
    val late = PickupRules.reduce(stopped, PickupEvent.Nav(NavEvent.Turn(turn)), 3_000L).state
    assertEquals(null, late.nextTurn)
  }
}
