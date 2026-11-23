package com.kscho.way.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.route.NavEvent
import com.kscho.way.domain.model.route.RoutePreview
import com.kscho.way.pickup.PickupNotifications
import com.kscho.way.pickup.WayScreen
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos NaviHelper 연결(에뮬 11/08 확인: requestTrip·requestRoute·getCurrentLocationInfo).
 *
 * requestRoute 하면 Pleos 내비가 화면 앞으로 나와 Way 를 가린다(SDK 에 끌 방법이 없다). 그래서
 * - 픽업 위치까지는 내비 안내를 걸지 않는다: 화면을 띄우지 않는 길 계산(requestTrip)과 차 위치 묻기로 Way 가 직접
 *   남은 시간·거리·도착을 어림한다([PickupTrack]). 운전자는 Way 화면(D-06~D-09)을 본다.
 * - 탑승한 뒤에야 내비로 넘긴다: 목적지가 있으면 [handOver](requestRoute), 없으면 [openNavi](기본 내비 화면).
 */
class PleosNavigator(context: Context, private val clock: () -> Long) : Navigator {
  private val appContext = context.applicationContext
  private val navi = NaviHelper(context.applicationContext)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val initStarted = AtomicBoolean(false)
  private val ready = CompletableDeferred<Boolean>()
  private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
  override val events: SharedFlow<NavEvent> = _events.asSharedFlow()
  private val trips = MutableSharedFlow<TripInfo>(extraBufferCapacity = 4)
  private val locations = MutableSharedFlow<LatLng>(extraBufferCapacity = 4)
  /** 길 계산 답(TripInfo)엔 어느 요청의 답인지 표시가 없어 한 번에 하나만 묻는다(픽업 추적 · 목적지 미리보기가 겹칠 수 있다). */
  private val tripAsk = Mutex()

  @Volatile private var lastLocation: LatLng? = null
  /** 픽업 위치까지 차 위치·남은 길 묻기. */
  private var tracking: Job? = null

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

  /** 지금 차 위치에서 [destination] 까지 길 계산(내비 화면을 띄우지 않는다). */
  private suspend fun trip(destination: LatLng, name: String): TripInfo? =
    tripAsk.withLock {
      ask(trips, 5_000) {
        navi.requestTrip(TripRequest(latitude = destination.latitude, longitude = destination.longitude, poiId = "", poiName = name, poiSubId = "0"))
      }
    }

  override suspend fun currentLocation(): LatLng? {
    if (!ensureReady()) return null
    return ask(locations, 3_000) { navi.getCurrentLocationInfo() } ?: lastLocation
  }

  override suspend fun preview(destination: LatLng, name: String): RoutePreview {
    val from = currentLocation()
    val trip = if (ensureReady()) trip(destination, name) else null
    if (trip != null) return RoutePreview(trip.distance, trip.eta, listOfNotNull(from, destination))
    // 내비가 답하지 않으면 직선거리로 어림(도심 25km/h, 우회 1.3배)
    Log.w(TAG, "requestTrip 응답 없음 — 직선거리로 어림")
    val meters = from?.let { Geo.distanceMeters(it, destination) * 1.3 } ?: 0.0
    return RoutePreview(meters.roundToInt(), (meters / 7.0).roundToInt(), listOfNotNull(from, destination))
  }

  /**
   * 출발: 픽업 위치까지 따라간다 — 내비 안내(requestRoute)는 걸지 않는다(내비가 앞으로 나와 Way 를 가리므로).
   * 3초마다 차 위치를 묻고 15초마다 남은 길을 다시 계산해 [NavEvent.Progress] 를, 가까이 오면 [NavEvent.Arrived] 를 낸다.
   */
  override suspend fun startGuidance(destination: LatLng, name: String, address: String) {
    check(ensureReady()) { "내비에 연결하지 못했어요" }
    tracking?.cancel()
    tracking =
      scope.launch {
        var trip: PickupTrack.Trip? = null
        var tripAt = 0L
        while (isActive) { // handOver·stopGuidance 가 이 일을 멈춘다
          val here = ask(locations, 3_000) { navi.getCurrentLocationInfo() } ?: lastLocation
          if (trip == null || clock() - tripAt >= PickupTrack.TRIP_REFRESH_MILLIS) {
            trip(destination, name)?.let {
              trip = PickupTrack.Trip(it.distance, it.eta, here)
              tripAt = clock()
            }
          }
          PickupTrack.progress(here, destination, trip, clock())?.let { _events.tryEmit(it) }
          if (PickupTrack.arrived(here, destination)) {
            _events.tryEmit(NavEvent.Arrived(clock()))
            break // 도착 뒤엔 더 따라가지 않는다(내비의 도착 알림처럼 한 번)
          }
          delay(PickupTrack.POLL_MILLIS)
        }
      }
  }

  /** 탑승 뒤 목적지: 이제야 내비 안내를 건다(내비가 앞으로 나온다). Way 는 더 따라가지 않는다(이 안내는 stopGuidance 로 끄지 않음). */
  override suspend fun handOver(destination: LatLng, name: String, address: String) {
    tracking?.cancel()
    tracking = null
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

  /**
   * D-11 목적지 없음: 기본 내비(지도 앱)를 앞으로 — 운전자가 음성으로 목적지를 정한다.
   * Pleos 내비는 CATEGORY_APP_MAPS 로 열린다(에뮬 11/14 확인) — Pleos 권한을 더 받지 않는다.
   * Way 가 화면 뒤면(동승자와 통화 중 등) 안드로이드가 말없이 막으므로 헤드업 알림 버튼으로 대신한다(문자 보내기와 같은 방식).
   */
  override fun openNavi() {
    val maps = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (WayScreen.isVisible(appContext)) {
      val opened = runCatching { appContext.startActivity(maps) }.onFailure { Log.w(TAG, "기본 내비를 열지 못했어요 — 알림으로 남김", it) }.isSuccess
      if (opened) return
    }
    PickupNotifications.createChannels(appContext)
    appContext.getSystemService(NotificationManager::class.java).notify(PickupNotifications.NAVI_ID, PickupNotifications.naviNeeded(appContext, maps))
  }

  /** 픽업을 끝내거나 취소: 따라가기만 멈춘다(픽업 구간엔 내비 안내를 걸지 않았으니 끌 것도 없다 — cancelRoute 는 내비를 앞으로 꺼낸다). */
  override fun stopGuidance() {
    tracking?.cancel()
    tracking = null
  }

  private companion object {
    const val TAG = "WayNavi"
  }
}
