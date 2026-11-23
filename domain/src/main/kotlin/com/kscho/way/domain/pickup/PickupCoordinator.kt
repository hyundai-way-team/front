package com.kscho.way.domain.pickup

import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.gateway.PassengerMessenger
import com.kscho.way.domain.model.base.ApiFailureException
import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.base.getOrThrow
import com.kscho.way.domain.model.message.PassengerText
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.route.NavEvent
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import com.kscho.way.domain.repository.pickup.PickupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PickupCoordinator(
  private val pickups: PickupRepository,
  private val history: PickupHistoryRepository,
  private val navigator: Navigator,
  private val boardingSensor: BoardingSensor,
  private val messenger: PassengerMessenger,
  private val scope: CoroutineScope,
  private val clock: () -> Long = System::currentTimeMillis,
  private val tickMillis: Long = 500L,
  private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {
  private val _state = MutableStateFlow<PickupState?>(null)
  val state: StateFlow<PickupState?> = _state.asStateFlow()

  private val _effects = MutableSharedFlow<PickupEffect>(extraBufferCapacity = 16)

  val effects: SharedFlow<PickupEffect> = _effects.asSharedFlow()

  private var events = Channel<PickupEvent>(Channel.UNLIMITED)
  private var job: Job? = null
  private var lastDriverReportAt: Long? = null
  private var handedOver = false
  private var autoHandOver: Job? = null

  val hasActivePickup: Boolean
    get() = _state.value?.let { !it.isBoarded } ?: false

  suspend fun start(plan: PickupPlan): PickupState {
    check(!hasActivePickup) { "이미 진행 중인 픽업이 있어요" }
    autoHandOver?.cancel()
    autoHandOver = null
    stopListening()
    _state.value = null
    val listening = listen()
    val point = plan.pickupPoint
    try {
      navigator.startGuidance(point.location, point.description.title, point.description.detail)
    } catch (e: Exception) {
      stopListening()
      throw e
    }
    val now = clock()
    val arrival = now + plan.route.durationSeconds * 1000L
    val created =
      when (val result = pickups.create(plan, arrival)) {
        is ApiState.Success -> result.data
        is ApiState.Fail -> {
          navigator.stopGuidance()
          stopListening()
          throw ApiFailureException(result.failState)
        }
      }
    val initial = PickupState(id = created.id, shareUrl = created.shareUrl, plan = plan, startedAtMillis = now, arrivalAtMillis = arrival)
    lastDriverReportAt = null
    handedOver = false
    _state.value = initial
    text("출발 문자", initial, PassengerText.Departure(created.shareUrl, plan.vehicle.driverName, plan.vehicle, point.description.title, arrival))
    listening.launch { pickups.passengerStatus(created.id).collect { events.send(PickupEvent.Passenger(it)) } }
    return initial
  }

  fun confirmBoarded(source: BoardingSource) {
    events.trySend(PickupEvent.Boarded(clock(), source))
  }

  fun arriveNow() {
    events.trySend(PickupEvent.Nav(NavEvent.Arrived(clock())))
  }

  @Synchronized
  fun continueToDestination(): Boolean {
    val s = _state.value ?: return false
    val destination = s.plan.destination ?: return false
    if (!s.isBoarded || handedOver) return false
    handedOver = true
    send("목적지 안내") { navigator.handOver(destination.location, destination.name, destination.address) }
    return true
  }

  @Synchronized
  fun connectNext() {
    val s = _state.value ?: return
    if (!s.isBoarded || s.nextCanceled) return
    autoHandOver?.cancel()
    autoHandOver = null
    if (s.plan.destination != null) {
      continueToDestination()
    } else if (!handedOver) {
      handedOver = true
      navigator.stopGuidance()
      try {
        navigator.openNavi()
      } catch (e: Exception) {
        onError("기본 내비 연결", e)
      }
    }
    stopListening()
    _state.value = null
  }

  @Synchronized
  fun cancelNext() {
    val s = _state.value ?: return
    if (!s.isBoarded || s.nextCanceled || handedOver) return
    autoHandOver?.cancel()
    autoHandOver = null
    val said = Announcement.GuidanceEnded
    _state.update { it?.copy(nextCanceled = true, lastAnnouncement = SpokenAnnouncement(said, clock())) }
    _effects.tryEmit(PickupEffect.Announce(said))
  }

  @Synchronized
  fun finish() {
    autoHandOver?.cancel()
    autoHandOver = null
    val s = _state.value
    if (s != null && s.isBoarded && !s.nextCanceled) return connectNext()
    val wasActive = hasActivePickup || s?.nextCanceled == true
    stopListening()
    if (wasActive) navigator.stopGuidance()
    _state.value = null
  }

  private fun listen(): CoroutineScope {
    val inbox = Channel<PickupEvent>(Channel.UNLIMITED)
    events = inbox
    val listening = Job(scope.coroutineContext[Job])
    val listenScope = CoroutineScope(scope.coroutineContext + listening)
    listenScope.launch(start = CoroutineStart.UNDISPATCHED) { navigator.events.collect { inbox.send(PickupEvent.Nav(it)) } }
    listenScope.launch(start = CoroutineStart.UNDISPATCHED) { boardingSensor.boardings.collect { inbox.send(PickupEvent.Boarded(it, BoardingSource.DOOR)) } }
    listenScope.launch {
      while (isActive) {
        delay(tickMillis)
        inbox.send(PickupEvent.Tick(clock()))
      }
    }
    listenScope.launch { for (event in inbox) handle(event) }
    job = listening
    return listenScope
  }

  private fun stopListening() {
    job?.cancel()
    job = null
  }

  private fun handle(event: PickupEvent) {
    val current = _state.value ?: return
    val now = clock()
    val (next, todo) = PickupRules.reduce(current, event, now)
    _state.value = next
    if (event is PickupEvent.Nav && event.event is NavEvent.Progress) reportDriver(next, now)
    todo.forEach { run(next, it) }
    if (next.isBoarded && !current.isBoarded) {
      stopListening()
      next.plan.destination?.let { estimateDestination(next.id, it) }
      scheduleConnect(next.boardedAtMillis ?: now)
    }
  }

  private fun scheduleConnect(boardedAt: Long) {
    autoHandOver?.cancel()
    autoHandOver =
      scope.launch {
        delay((boardedAt + PickupPolicy.CONNECT_AFTER_BOARDING_SECONDS * 1000L - clock()).coerceAtLeast(0))
        autoHandOver = null
        connectNext()
      }
  }

  private fun estimateDestination(pickupId: String, destination: Place) {
    scope.launch {
      val trip =
        try {
          navigator.preview(destination.location, destination.name)
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          onError("목적지 도착 예정", e)
          return@launch
        }
      val arrivalAt = clock() + trip.durationSeconds * 1000L
      _state.update { s -> if (s?.id == pickupId) s.copy(destinationTripSeconds = trip.durationSeconds, destinationArrivalAtMillis = arrivalAt) else s }
    }
  }

  private fun reportDriver(state: PickupState, now: Long) {
    val last = lastDriverReportAt
    if (last != null && now - last < PickupPolicy.DRIVER_REPORT_INTERVAL_MILLIS) return
    lastDriverReportAt = now
    val remainingSeconds = state.remainingSeconds ?: return
    val remainingMeters = state.remainingMeters ?: return
    report("차량 위치 보고") { pickups.reportDriver(state.id, state.carLocation, remainingSeconds, remainingMeters, state.arrivalAtMillis) }
  }

  private fun run(state: PickupState, effect: PickupEffect) {
    when (effect) {
      is PickupEffect.Announce,
      is PickupEffect.OfferCall -> _effects.tryEmit(effect)
      is PickupEffect.NotifyEtaChanged -> {
        text("ETA 변경 문자", state, PassengerText.EtaChanged(state.shareUrl, effect.previousArrivalAtMillis, effect.arrivalAtMillis))
        report("ETA 변경 알림") { pickups.notifyEtaChanged(state.id, effect.previousArrivalAtMillis, effect.arrivalAtMillis) }
      }
      is PickupEffect.NotifyArrivingSoon -> {
        text("곧 도착 문자", state, PassengerText.ArrivingSoon(state.shareUrl))
        report("곧 도착 알림") { pickups.notifyArrivingSoon(state.id, effect.arrivalAtMillis) }
      }
      is PickupEffect.ReportArrived -> {
        lastDriverReportAt = effect.atMillis
        val last = state.carLocation ?: state.plan.pickupPoint.location
        report("정차 위치 보고") { pickups.reportDriver(state.id, last, 0, 0, effect.atMillis) }
        report("정차 보고") { pickups.reportArrived(state.id, effect.atMillis, PickupPolicy.WAIT_SECONDS) }
      }
      is PickupEffect.Complete -> {
        report("탑승 완료 보고") { pickups.complete(state.id, effect.outcome) }
        val record = PickupRecords.of(state, effect.outcome, clock())
        send("픽업 기록 저장") { history.save(record) }
      }
    }
  }

  private fun text(what: String, state: PickupState, text: PassengerText) = send(what) { messenger.send(state.plan.passenger, text) }

  private fun report(what: String, block: suspend () -> ApiState<Unit>) = send(what) { block().getOrThrow() }

  private fun send(what: String, block: suspend () -> Unit) {
    scope.launch {
      try {
        block()
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        onError(what, e)
      }
    }
  }
}
