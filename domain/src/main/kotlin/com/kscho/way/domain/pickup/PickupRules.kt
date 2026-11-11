package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.PickupOutcome
import kotlin.math.abs

/** 픽업 중 일어나는 일. */
sealed interface PickupEvent {
  data class Nav(val event: NavEvent) : PickupEvent

  data class Passenger(val status: PassengerStatus) : PickupEvent

  data class Boarded(val atMillis: Long, val source: BoardingSource) : PickupEvent

  data class Tick(val nowMillis: Long) : PickupEvent
}

/** 규칙이 정한 할 일 — 실행(문자·음성·서버 보고)은 [PickupCoordinator] 와 앱이 한다. */
sealed interface PickupEffect {
  data class Announce(val announcement: Announcement) : PickupEffect

  /** 음성으로 전화 연결을 묻는다(대답을 듣는 질문). */
  data class OfferCall(val announcement: Announcement.CallOffer) : PickupEffect

  data class NotifyEtaChanged(val previousArrivalAtMillis: Long, val arrivalAtMillis: Long) : PickupEffect

  data class NotifyArrivingSoon(val arrivalAtMillis: Long) : PickupEffect

  data class ReportArrived(val atMillis: Long) : PickupEffect

  data class Complete(val outcome: PickupOutcome) : PickupEffect
}

data class Transition(val state: PickupState, val effects: List<PickupEffect> = emptyList())

/**
 * 기획 3-5 ~ 3-10 규칙. 순수 함수라 시간·순서를 단위 테스트로 고정한다.
 * 탑승 뒤엔 아무 일도 바꾸지 않는다.
 */
object PickupRules {
  fun reduce(state: PickupState, event: PickupEvent, nowMillis: Long): Transition {
    if (state.isBoarded) return Transition(state)
    return when (event) {
      is PickupEvent.Nav ->
        when (val nav = event.event) {
          is NavEvent.Progress -> onProgress(state, nav, nowMillis)
          is NavEvent.Arrived -> onArrived(state, nav.atMillis)
        }
      is PickupEvent.Passenger -> onPassenger(state, event.status, nowMillis)
      is PickupEvent.Boarded -> onBoarded(state, event, nowMillis)
      is PickupEvent.Tick -> onTick(state, event.nowMillis)
    }
  }

  private fun onProgress(state: PickupState, nav: NavEvent.Progress, now: Long): Transition {
    if (state.phase != PickupPhase.DRIVING) return Transition(state)
    var next =
      state.copy(
        carLocation = nav.location ?: state.carLocation,
        remainingSeconds = nav.remainingSeconds,
        remainingMeters = nav.remainingMeters,
        arrivalAtMillis = nav.arrivalAtMillis,
      )
    val effects = mutableListOf<PickupEffect>()

    // 3-5 ETA 가 기준 이상 바뀌면 다시 알린다
    val moved = abs(nav.arrivalAtMillis - state.notifiedArrivalAtMillis)
    if (moved >= PickupPolicy.ETA_RESEND_THRESHOLD_SECONDS * 1000L) {
      effects += PickupEffect.NotifyEtaChanged(state.notifiedArrivalAtMillis, nav.arrivalAtMillis)
      next = next.copy(notifiedArrivalAtMillis = nav.arrivalAtMillis, etaChangeNotices = state.etaChangeNotices + 1)
    }

    // 3-6 도착 5분 전 "곧 도착해요" — 1회
    if (!state.arrivingSoonSent && nav.remainingSeconds <= PickupPolicy.ARRIVING_SOON_SECONDS) {
      val said = Announcement.ArrivingSoonSent(state.plan.passenger)
      effects += PickupEffect.NotifyArrivingSoon(nav.arrivalAtMillis)
      effects += PickupEffect.Announce(said)
      next = next.copy(arrivingSoonSent = true, lastAnnouncement = SpokenAnnouncement(said, now))
    }
    return Transition(next, effects)
  }

  private fun onArrived(state: PickupState, at: Long): Transition {
    if (state.phase != PickupPhase.DRIVING) return Transition(state)
    val next = state.copy(phase = PickupPhase.WAITING, arrivedAtMillis = at, remainingSeconds = 0, remainingMeters = 0)
    return Transition(next, listOf(PickupEffect.ReportArrived(at)))
  }

  private fun onPassenger(state: PickupState, status: PassengerStatus, now: Long): Transition {
    val said = buildList {
      // 3-8 링크 확인 안내 — 처음 열었을 때만
      if (!state.passenger.linkOpened && status.linkOpened) add(Announcement.LinkChecked(state.plan.passenger))
      // 동승자 '늦어요' — 처음 눌렀을 때만
      if (!state.passenger.late && status.late) add(Announcement.PassengerLate(state.plan.passenger))
    }
    val next = state.copy(passenger = status)
    if (said.isEmpty()) return Transition(next)
    return Transition(next.copy(lastAnnouncement = SpokenAnnouncement(said.last(), now)), said.map { PickupEffect.Announce(it) })
  }

  private fun onTick(state: PickupState, now: Long): Transition {
    if (state.phase != PickupPhase.WAITING || state.callOffered) return Transition(state)
    val remaining = state.waitRemainingMillis(now) ?: return Transition(state)
    if (remaining > PickupPolicy.CALL_OFFER_REMAINING_SECONDS * 1000L) return Transition(state)
    // 3-9 15초 남았는데 미탑승 → 음성으로 전화 연결 제안(1회)
    val ask = Announcement.CallOffer(state.plan.passenger)
    return Transition(
      state.copy(callOffered = true, lastAnnouncement = SpokenAnnouncement(ask, now)),
      listOf(PickupEffect.OfferCall(ask)),
    )
  }

  private fun onBoarded(state: PickupState, event: PickupEvent.Boarded, now: Long): Transition {
    // 3-10 정차 전에 타도 그 시점에 끝낸다(예외 케이스)
    val next =
      state.copy(
        phase = PickupPhase.BOARDED,
        boardedAtMillis = event.atMillis,
        boardingSource = event.source,
        lastAnnouncement = SpokenAnnouncement(Announcement.Boarded, now),
      )
    val outcome =
      PickupOutcome(
        boarded = true,
        boardedAtMillis = event.atMillis,
        waitedSeconds = next.waitedSeconds,
        pickupLocationChanges = 0,
        boardingSource = event.source,
      )
    return Transition(next, listOf(PickupEffect.Complete(outcome), PickupEffect.Announce(Announcement.Boarded)))
  }
}
