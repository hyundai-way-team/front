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
import kotlinx.coroutines.flow.update
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
  /** 탑승 뒤 다음으로 이었는지 — 목적지 안내를 넘겼거나 기본 내비를 띄웠다(한 번만). */
  private var handedOver = false
  /** 탑승 [PickupPolicy.CONNECT_AFTER_BOARDING_SECONDS] 초 뒤 다음으로 잇고 픽업을 닫는 일(D-11). */
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
   * 정해 둔 목적지 안내를 Pleos 내비에 넘긴다. 넘겼으면 true.
   * 탑승 전이거나 목적지가 없거나 이미 넘겼으면 아무것도 하지 않는다. 픽업은 닫지 않는다 — 닫으며 넘기려면 [connectNext].
   */
  @Synchronized // 화면 버튼과 5초 타이머가 다른 스레드에서 겹쳐도 한 번만
  fun continueToDestination(): Boolean {
    val s = _state.value ?: return false
    val destination = s.plan.destination ?: return false
    if (!s.isBoarded || handedOver) return false
    handedOver = true
    send("목적지 안내") { navigator.handOver(destination.location, destination.name, destination.address) }
    return true
  }

  /**
   * D-11 '안내 시작' · '지금 연결' · 5초가 지났을 때: 탑승 뒤 다음으로 잇고 픽업을 닫는다.
   * 목적지가 있으면 그곳 안내를 Pleos 내비에 넘기고, 없으면 기본 내비를 띄운다. 탑승 전이면 아무것도 하지 않는다.
   */
  @Synchronized
  fun connectNext() {
    val s = _state.value ?: return
    // '취소'한 뒤엔 잇지 않는다 — 5초 타이머가 '취소'와 겹쳐 늦게 들어와도
    if (!s.isBoarded || s.nextCanceled) return
    autoHandOver?.cancel()
    autoHandOver = null
    if (s.plan.destination != null) {
      continueToDestination()
    } else if (!handedOver) {
      handedOver = true
      // 픽업 위치 안내를 거두고(이때 내비가 앞으로 나온다) 기본 내비로
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

  /**
   * D-11 '취소': 5초 뒤 잇지 않고 D-10 에 남는다. 이후 [finish] 로 닫아도 목적지 안내·내비 연결은 하지 않는다.
   * 정차 전에 탔다면 아직 켜진 픽업 위치 안내는 D-10 을 닫을 때 거둔다 — 지금 거두면 Pleos 내비가 앞으로 나와 D-10 을 가린다.
   */
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

  /** 안내 종료 화면을 닫는다(또는 픽업을 접는다). 탑승 뒤 '취소'하지 않았으면 다음으로 이으며 닫는다([connectNext]). */
  @Synchronized
  fun finish() {
    autoHandOver?.cancel()
    autoHandOver = null
    val s = _state.value
    if (s != null && s.isBoarded && !s.nextCanceled) return connectNext()
    // 탑승 전(픽업 접기) · D-11 '취소' 뒤 D-10 닫기 — 남은 픽업 위치 안내를 거둔다(이미 도착해 끝났으면 내비가 알아서 넘긴다)
    val wasActive = hasActivePickup || s?.nextCanceled == true
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
      // D-11: 5초 뒤 다음으로 잇는다 — 목적지가 있으면 그곳 안내를 넘기고(걸리는 시간은 지금 내비에 묻는다), 없으면 기본 내비로.
      // 픽업 위치 안내는 그때 거둔다: 거두면 Pleos 내비가 앞으로 나와 D-11(5초·취소)을 가린다.
      // 5초는 화면이 아니라 여기서 센다 — 실차에선 Pleos 내비가 앞에 있어 Way 화면이 멈춰 있기도 하다.
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

  /** D-11 '10:05 도착 예정 (약 15분)' — 탑승한 자리에서 목적지까지. 내비가 답하지 않으면 비워 둔다(화면은 그 줄을 빼고 보여 준다). */
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
