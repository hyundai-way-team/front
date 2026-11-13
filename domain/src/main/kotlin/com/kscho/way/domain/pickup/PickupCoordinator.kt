package com.kscho.way.domain.pickup

import com.kscho.way.domain.messaging.PassengerMessenger
import com.kscho.way.domain.messaging.PassengerText
import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.PickupPlan
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.repository.PickupHistoryRepository
import com.kscho.way.domain.repository.PickupRepository
import com.kscho.way.domain.vehicle.BoardingSensor
import com.kscho.way.domain.vehicle.Navigator
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 진행 중인 픽업 1건을 끝까지 끌고 간다.
 * 내비(진행·도착) · 서버(동승자 상태) · 도어(탑승) · 시계를 한 줄로 모아 [PickupRules] 에 넣고,
 * 규칙이 정한 일(문자·서버 보고)은 여기서, 음성은 [effects] 를 받은 앱이 한다.
 * 문자는 차가 운전자 번호로 보내고([messenger]), 서버에는 같은 일을 알려 동승자 웹 화면을 바꾼다.
 * 끝난 픽업 기록은 앱에 남긴다([history]).
 *
 * 앱 전체에서 하나만 둔다(화면·포그라운드 서비스·Gleo 가 같은 [state] 를 본다).
 */
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

  /** 음성으로 할 일([PickupEffect.Announce] · [PickupEffect.OfferCall]). */
  val effects: SharedFlow<PickupEffect> = _effects.asSharedFlow()

  private var events = Channel<PickupEvent>(Channel.UNLIMITED)
  private var job: Job? = null
  private var lastDriverReportAt: Long? = null
  /** 탑승 뒤 목적지 안내를 내비에 넘겼는지(한 번만). */
  private var handedOver = false
  /** 탑승 [PickupPolicy.DESTINATION_HANDOVER_SECONDS] 초 뒤 목적지 안내를 넘기고 픽업을 닫는 일. */
  private var autoHandOver: Job? = null

  /** 탑승 전인 픽업이 있는지. */
  val hasActivePickup: Boolean
    get() = _state.value?.let { !it.isBoarded } ?: false

  /**
   * 출발(D-05). 내비 안내를 먼저 켜고, 서버에 세션을 만들어 참여 링크를 받은 뒤 차에서 동승자에게 문자(차량 번호·픽업 위치·ETA·링크)를 보낸다.
   * 내비를 못 켜면 문자를 보내지 않고, 서버가 실패하면(링크가 없으니 문자도 없다) 내비 안내를 거둔다 — 어느 쪽이든 픽업은 남지 않는다.
   */
  suspend fun start(plan: PickupPlan): PickupState {
    check(!hasActivePickup) { "이미 진행 중인 픽업이 있어요" }
    autoHandOver?.cancel()
    autoHandOver = null
    stopListening()
    _state.value = null
    // 구독을 먼저 걸어 둔다 — 내비 안내 시작 직후 오는 일을 놓치지 않게(세션이 생기기 전 일은 버린다)
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
      try {
        pickups.create(plan, arrival)
      } catch (e: Exception) {
        navigator.stopGuidance()
        stopListening()
        throw e
      }
    val initial = PickupState(id = created.id, shareUrl = created.shareUrl, plan = plan, startedAtMillis = now, arrivalAtMillis = arrival)
    lastDriverReportAt = null
    handedOver = false
    _state.value = initial
    text("출발 문자", initial, PassengerText.Departure(created.shareUrl, plan.vehicle.driverName, plan.vehicle, point.description.title, arrival))
    listening.launch { pickups.passengerStatus(created.id).collect { events.send(PickupEvent.Passenger(it)) } }
    return initial
  }

  /** 도어 이벤트를 못 쓸 때의 버튼 · Gleo "탑승 완료". */
  fun confirmBoarded(source: BoardingSource) {
    events.trySend(PickupEvent.Boarded(clock(), source))
  }

  /** 시연·디버그용: 지금 정차한 것으로. */
  fun arriveNow() {
    events.trySend(PickupEvent.Nav(NavEvent.Arrived(clock())))
  }

  /**
   * D-10: 정해 둔 목적지 안내를 Pleos 내비에 넘긴다('탑승 완료' 버튼 · 10초 카운트다운). 넘겼으면 true.
   * 탑승 전이거나 목적지가 없거나 이미 넘겼으면 아무것도 하지 않는다.
   */
  @Synchronized // 화면('탑승 완료')과 10초 타이머가 다른 스레드에서 겹쳐도 한 번만
  fun continueToDestination(): Boolean {
    val s = _state.value ?: return false
    val destination = s.plan.destination ?: return false
    if (!s.isBoarded || handedOver) return false
    handedOver = true
    send("목적지 안내") { navigator.handOver(destination.location, destination.name, destination.address) }
    return true
  }

  /** 안내 종료 화면을 닫는다(또는 픽업을 접는다). 목적지를 정해 두고 아직 넘기지 않았으면 넘긴다. */
  @Synchronized
  fun finish() {
    autoHandOver?.cancel()
    autoHandOver = null
    continueToDestination()
    val wasActive = hasActivePickup
    stopListening()
    if (wasActive) navigator.stopGuidance()
    _state.value = null
  }

  /** 내비·도어·시계를 한 줄로 모으는 구독을 건다. 돌려준 scope 에 더 붙이면 함께 멈춘다. */
  private fun listen(): CoroutineScope {
    val inbox = Channel<PickupEvent>(Channel.UNLIMITED)
    events = inbox
    val listening = Job(scope.coroutineContext[Job])
    val listenScope = CoroutineScope(scope.coroutineContext + listening)
    // UNDISPATCHED: hot flow 구독이 이 줄에서 바로 걸린다
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
      // 목적지가 없으면 픽업 안내를 끝낸다. 있으면 D-10 에서 운전자('탑승 완료')를 기다리되, 10초가 지나면 스스로 넘긴다.
      // 화면이 아니라 여기서 센다 — 실차에선 Pleos 내비가 앞에 있어 Way 화면(D-10)이 멈춰 있다.
      if (next.plan.destination == null) navigator.stopGuidance() else scheduleHandOver(next.boardedAtMillis ?: now)
    }
  }

  private fun scheduleHandOver(boardedAt: Long) {
    autoHandOver?.cancel()
    autoHandOver =
      scope.launch {
        delay((boardedAt + PickupPolicy.DESTINATION_HANDOVER_SECONDS * 1000L - clock()).coerceAtLeast(0))
        autoHandOver = null
        finish()
      }
  }

  private fun reportDriver(state: PickupState, now: Long) {
    val last = lastDriverReportAt
    if (last != null && now - last < PickupPolicy.DRIVER_REPORT_INTERVAL_MILLIS) return
    lastDriverReportAt = now
    val remainingSeconds = state.remainingSeconds ?: return
    val remainingMeters = state.remainingMeters ?: return
    send("차량 위치 보고") { pickups.reportDriver(state.id, state.carLocation, remainingSeconds, remainingMeters, state.arrivalAtMillis) }
  }

  private fun run(state: PickupState, effect: PickupEffect) {
    when (effect) {
      is PickupEffect.Announce,
      is PickupEffect.OfferCall -> _effects.tryEmit(effect)
      is PickupEffect.NotifyEtaChanged -> {
        text("ETA 변경 문자", state, PassengerText.EtaChanged(state.shareUrl, effect.previousArrivalAtMillis, effect.arrivalAtMillis))
        send("ETA 변경 알림") { pickups.notifyEtaChanged(state.id, effect.previousArrivalAtMillis, effect.arrivalAtMillis) }
      }
      is PickupEffect.NotifyArrivingSoon -> {
        text("곧 도착 문자", state, PassengerText.ArrivingSoon(state.shareUrl))
        send("곧 도착 알림") { pickups.notifyArrivingSoon(state.id, effect.arrivalAtMillis) }
      }
      is PickupEffect.ReportArrived -> {
        // 서기 직전 위치를 바로 보낸다 — 5초 간격 보고만으론 동승자 지도의 차가 픽업 지점 앞에서 멈춰 보인다.
        // 내비 위치를 한 번도 못 받았으면 도착한 곳 = 픽업 지점
        lastDriverReportAt = effect.atMillis
        val last = state.carLocation ?: state.plan.pickupPoint.location
        send("정차 위치 보고") { pickups.reportDriver(state.id, last, 0, 0, effect.atMillis) }
        send("정차 보고") { pickups.reportArrived(state.id, effect.atMillis, PickupPolicy.WAIT_SECONDS) }
      }
      is PickupEffect.Complete -> {
        send("탑승 완료 보고") { pickups.complete(state.id, effect.outcome) }
        val record = PickupRecords.of(state, effect.outcome, clock())
        send("픽업 기록 저장") { history.save(record) }
      }
    }
  }

  /** 차에서 동승자에게 문자 — 서버 알림과 따로(서버가 안 돼도 문자는 나간다). */
  private fun text(what: String, state: PickupState, text: PassengerText) = send(what) { messenger.send(state.plan.passenger, text) }

  // 픽업이 끝나도(구독 job 취소) 서버 보고는 마치도록 scope 에서 따로 돈다
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
