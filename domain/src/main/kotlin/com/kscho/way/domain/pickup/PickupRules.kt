package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.model.pickup.PickupOutcome
import com.kscho.way.domain.model.route.NavEvent
import kotlin.math.abs

sealed interface PickupEvent {
  data class Nav(val event: NavEvent) : PickupEvent

  data class Passenger(val status: PassengerStatus) : PickupEvent

  data class Boarded(val atMillis: Long, val source: BoardingSource) : PickupEvent

  data class Tick(val nowMillis: Long) : PickupEvent
}

sealed interface PickupEffect {
  data class Announce(val announcement: Announcement) : PickupEffect

  data class OfferCall(val announcement: Announcement.CallOffer) : PickupEffect

  data class NotifyEtaChanged(val previousArrivalAtMillis: Long, val arrivalAtMillis: Long) : PickupEffect

  data class NotifyArrivingSoon(val arrivalAtMillis: Long) : PickupEffect

  data class ReportArrived(val atMillis: Long) : PickupEffect

  data class Complete(val outcome: PickupOutcome) : PickupEffect
}

data class Transition(val state: PickupState, val effects: List<PickupEffect> = emptyList())

object PickupRules {
  fun reduce(state: PickupState, event: PickupEvent, nowMillis: Long): Transition {
    if (state.isBoarded) return Transition(state)
    return when (event) {
      is PickupEvent.Nav ->
        when (val nav = event.event) {
          is NavEvent.Progress -> onProgress(state, nav, nowMillis)
          is NavEvent.Arrived -> onArrived(state, nav.atMillis)
          is NavEvent.Turn -> Transition(if (state.phase == PickupPhase.DRIVING) state.copy(nextTurn = nav.instruction) else state)
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

    val moved = abs(nav.arrivalAtMillis - state.notifiedArrivalAtMillis)
    if (moved >= PickupPolicy.ETA_RESEND_THRESHOLD_SECONDS * 1000L) {
      effects += PickupEffect.NotifyEtaChanged(state.notifiedArrivalAtMillis, nav.arrivalAtMillis)
      next = next.copy(notifiedArrivalAtMillis = nav.arrivalAtMillis, etaChangeNotices = state.etaChangeNotices + 1)
    }

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
    val next = state.copy(phase = PickupPhase.WAITING, arrivedAtMillis = at, remainingSeconds = 0, remainingMeters = 0, nextTurn = null)
    return Transition(next, listOf(PickupEffect.ReportArrived(at)))
  }

  private fun onPassenger(state: PickupState, status: PassengerStatus, now: Long): Transition {
    val said = buildList {
      if (!state.passenger.linkOpened && status.linkOpened) add(Announcement.LinkChecked(state.plan.passenger))
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
    val ask = Announcement.CallOffer(state.plan.passenger)
    return Transition(
      state.copy(callOffered = true, lastAnnouncement = SpokenAnnouncement(ask, now)),
      listOf(PickupEffect.OfferCall(ask)),
    )
  }

  private fun onBoarded(state: PickupState, event: PickupEvent.Boarded, now: Long): Transition {
    val said = Announcement.Boarded(destinationName = state.plan.destination?.name)
    val next =
      state.copy(
        phase = PickupPhase.BOARDED,
        boardedAtMillis = event.atMillis,
        boardingSource = event.source,
        lastAnnouncement = SpokenAnnouncement(said, now),
      )
    val outcome =
      PickupOutcome(
        boarded = true,
        boardedAtMillis = event.atMillis,
        waitedSeconds = next.waitedSeconds,
        pickupLocationChanges = 0,
        boardingSource = event.source,
      )
    return Transition(next, listOf(PickupEffect.Complete(outcome), PickupEffect.Announce(said)))
  }
}
