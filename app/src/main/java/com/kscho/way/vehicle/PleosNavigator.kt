package com.kscho.way.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.RoutePreview
import com.kscho.way.domain.model.TurnInstruction
import com.kscho.way.domain.model.TurnKind
import com.kscho.way.domain.vehicle.Navigator
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos NaviHelper 로 실제 내비 안내(에뮬 11/08 확인: requestTrip·requestRoute·onDrivingInfo·onDestinationArrived).
 * requestRoute 하면 Pleos 내비가 앞으로 나와 Way 는 뒤로 간다 — 픽업 서비스(포그라운드)가 Way 를 살려 둔다.
 */
class PleosNavigator(context: Context, private val clock: () -> Long) : Navigator {
  private val navi = NaviHelper(context.applicationContext)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val initStarted = AtomicBoolean(false)
  private val ready = CompletableDeferred<Boolean>()
  private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
  override val events: SharedFlow<NavEvent> = _events.asSharedFlow()
  private val trips = MutableSharedFlow<TripInfo>(extraBufferCapacity = 4)
  private val locations = MutableSharedFlow<LatLng>(extraBufferCapacity = 4)

  @Volatile private var lastLocation: LatLng? = null
  /** 픽업 위치 안내 — 요청한 경로가 시작되기 전 소식(지난 경로의 남은 0초 등)은 버린다. */
  private val route = RouteSession()
  private var poll: Job? = null

  private val listener =
    object : NaviHelperEventListener {
      override fun onTripInfo(tripInfo: TripInfo) {
        trips.tryEmit(tripInfo)
      }

      override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
        val here = LatLng(currentLocationInfo.latitude, currentLocationInfo.longitude)
        lastLocation = here
        locations.tryEmit(here)
      }

      override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        route.started()
      }

      override fun onDrivingInfo(drivingInfo: DrivingInfo) {
        if (!route.live) return
        val dest = drivingInfo.destination ?: return
        _events.tryEmit(NavEvent.Progress(lastLocation, dest.duration, dest.distance, clock() + dest.duration * 1000L))
      }

      override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
        if (!route.live) return
        val next = tbtInfoList.firstOrNull() ?: return
        _events.tryEmit(NavEvent.Turn(TurnInstruction(turnKind(next.type), next.distance.roundToInt(), next.description)))
      }

      override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
        if (!route.arrive()) return
        _events.tryEmit(NavEvent.Arrived(clock()))
      }

      override fun onError(errorCode: NaviErrorCode) {
        Log.w(TAG, "NaviHelper 오류 $errorCode")
      }
    }

  private suspend fun ensureReady(): Boolean {
    if (initStarted.compareAndSet(false, true)) {
      navi.initialize(
        {
          navi.addListener(listener)
          ready.complete(true)
        },
        { e ->
          Log.w(TAG, "NaviHelper 초기화 실패", e)
          ready.complete(false)
        },
      )
    }
    return withTimeoutOrNull(5_000) { ready.await() } ?: false
  }

  /** [call] 로 요청하고 [flow] 의 첫 답을 기다린다(요청 전에 먼저 구독). */
  private suspend fun <T> ask(flow: SharedFlow<T>, timeoutMillis: Long, call: () -> Unit): T? =
    withTimeoutOrNull(timeoutMillis) {
      coroutineScope {
        val answer = async(start = CoroutineStart.UNDISPATCHED) { flow.first() }
        call()
        answer.await()
      }
    }

  override suspend fun currentLocation(): LatLng? {
    if (!ensureReady()) return null
    return ask(locations, 3_000) { navi.getCurrentLocationInfo() } ?: lastLocation
  }

  override suspend fun preview(destination: LatLng, name: String): RoutePreview {
    val from = currentLocation()
    val trip =
      if (ensureReady()) {
        ask(trips, 5_000) {
          navi.requestTrip(TripRequest(latitude = destination.latitude, longitude = destination.longitude, poiId = "", poiName = name, poiSubId = "0"))
        }
      } else {
        null
      }
    if (trip != null) return RoutePreview(trip.distance, trip.eta, listOfNotNull(from, destination))
    // 내비가 답하지 않으면 직선거리로 어림(도심 25km/h, 우회 1.3배)
    Log.w(TAG, "requestTrip 응답 없음 — 직선거리로 어림")
    val meters = from?.let { Geo.distanceMeters(it, destination) * 1.3 } ?: 0.0
    return RoutePreview(meters.roundToInt(), (meters / 7.0).roundToInt(), listOfNotNull(from, destination))
  }

  override suspend fun startGuidance(destination: LatLng, name: String, address: String) {
    check(ensureReady()) { "내비에 연결하지 못했어요" }
    route.requested()
    navi.requestRoute(
      RouteInfo(
        latitude = destination.latitude,
        longitude = destination.longitude,
        displayLatitude = null,
        displayLongitude = null,
        poiName = name,
        poiId = "",
        address = address,
        poiSubId = "0",
      )
    )
    // 차량 위치는 요청해야 오므로 안내 중엔 3초마다 묻는다
    poll?.cancel()
    poll =
      scope.launch {
        while (isActive) { // handOver·stopGuidance 가 이 일을 멈춘다
          navi.getCurrentLocationInfo()
          delay(3_000)
        }
      }
  }

  /** 탑승 뒤 목적지: 내비 안내만 걸고 위치 묻기·도착 처리는 하지 않는다(이후 stopGuidance 로 이 안내를 끄지 않음). */
  override suspend fun handOver(destination: LatLng, name: String, address: String) {
    poll?.cancel()
    poll = null
    route.stopped()
    check(ensureReady()) { "내비에 연결하지 못했어요" }
    navi.requestRoute(
      RouteInfo(
        latitude = destination.latitude,
        longitude = destination.longitude,
        displayLatitude = null,
        displayLongitude = null,
        poiName = name,
        poiId = "",
        address = address,
        poiSubId = "0",
      )
    )
  }

  override fun stopGuidance() {
    if (route.guidingUnarrived) runCatching { navi.cancelRoute() }
    route.stopped()
    poll?.cancel()
    poll = null
  }

  private companion object {
    /** Pleos 회전 종류 → 화면 문구·화살표(오른쪽·왼쪽 계열로 묶는다). */
    fun turnKind(type: TurnType): TurnKind =
      when (type) {
        TurnType.TURN_STRAIGHT -> TurnKind.STRAIGHT
        TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> TurnKind.RIGHT
        TurnType.TURN_LEFT, TurnType.TURN_SHARP_L -> TurnKind.LEFT
        TurnType.TURN_KEEP_R, TurnType.TURN_ENTER_IC_R, TurnType.TURN_EXIT_IC_R, TurnType.TURN_ENTER_UNDER_R, TurnType.TURN_AVOID_UNDER_R,
        TurnType.TURN_ENTER_OVER_R, TurnType.TURN_AVOID_OVER_R -> TurnKind.KEEP_RIGHT
        TurnType.TURN_KEEP_L, TurnType.TURN_ENTER_IC_L, TurnType.TURN_EXIT_IC_L, TurnType.TURN_ENTER_UNDER_L, TurnType.TURN_AVOID_UNDER_L,
        TurnType.TURN_ENTER_OVER_L, TurnType.TURN_AVOID_OVER_L -> TurnKind.KEEP_LEFT
        TurnType.TURN_UTURN -> TurnKind.U_TURN
        TurnType.TURN_ENTER_IC_C, TurnType.TURN_EXIT_IC_C, TurnType.TURN_ENTER_UNDER_C, TurnType.TURN_AVOID_UNDER_C, TurnType.TURN_ENTER_OVER_C,
        TurnType.TURN_AVOID_OVER_C, TurnType.TURN_TUNNEL, TurnType.TURN_TOLL -> TurnKind.STRAIGHT
        else -> TurnKind.OTHER
      }

    const val TAG = "WayNavi"
  }
}
