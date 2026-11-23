package com.kscho.way.data.device.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kscho.way.data.device.launcher.ScreenLauncher
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.route.NavEvent
import com.kscho.way.domain.model.route.RoutePreview
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

class PleosNavigator(
  context: Context,
  private val clock: () -> Long,
  private val screenLauncher: ScreenLauncher,
) : Navigator {
  private val appContext = context.applicationContext
  private val navi = NaviHelper(context.applicationContext)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val initStarted = AtomicBoolean(false)
  private val ready = CompletableDeferred<Boolean>()
  private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
  override val events: SharedFlow<NavEvent> = _events.asSharedFlow()
  private val trips = MutableSharedFlow<TripInfo>(extraBufferCapacity = 4)
  private val locations = MutableSharedFlow<LatLng>(extraBufferCapacity = 4)
  private val tripAsk = Mutex()

  @Volatile private var lastLocation: LatLng? = null
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

  private suspend fun <T> ask(flow: SharedFlow<T>, timeoutMillis: Long, call: () -> Unit): T? =
    withTimeoutOrNull(timeoutMillis) {
      coroutineScope {
        val answer = async(start = CoroutineStart.UNDISPATCHED) { flow.first() }
        call()
        answer.await()
      }
    }

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
    Log.w(TAG, "requestTrip 응답 없음 — 직선거리로 어림")
    val meters = from?.let { Geo.distanceMeters(it, destination) * 1.3 } ?: 0.0
    return RoutePreview(meters.roundToInt(), (meters / 7.0).roundToInt(), listOfNotNull(from, destination))
  }

  override suspend fun startGuidance(destination: LatLng, name: String, address: String) {
    check(ensureReady()) { "내비에 연결하지 못했어요" }
    tracking?.cancel()
    tracking =
      scope.launch {
        var trip: PickupTrack.Trip? = null
        var tripAt = 0L
        while (isActive) {
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
            break
          }
          delay(PickupTrack.POLL_MILLIS)
        }
      }
  }

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

  override fun openNavi() {
    screenLauncher.openNavi(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  override fun stopGuidance() {
    tracking?.cancel()
    tracking = null
  }

  private companion object {
    const val TAG = "WayNavi"
  }
}
