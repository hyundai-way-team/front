package com.kscho.way.debug

import com.kscho.way.data.fake.SimulatedNavigator
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.route.NavEvent
import com.kscho.way.domain.model.route.RoutePreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 디버그 빌드 전용 시연 주행 — 에뮬레이터의 Pleos 내비는 차 위치가 움직이지 않아(GPS 를 바꿔도 시흥동 그대로)
 * 픽업 위치로 다가오는 모습을 보일 수 없다. adb 로 실제 도로 경로를 넣으면([setRoute]) 픽업까지는 그 길을 빨리 달리고,
 * 탑승 뒤 내비로 넘기는 것([handOver]·[openNavi])은 진짜 Pleos 내비에 맡긴다. 경로를 넣지 않으면 진짜 내비 그대로.
 *
 *   adb shell am broadcast --user 10 -a com.kscho.way.debug.DEMO_ROUTE -p com.kscho.way \
 *     --es path "37.51,127.01;37.512,127.012;…" --ei speedup 6 --ef speed 8
 *   adb shell am broadcast --user 10 -a com.kscho.way.debug.DEMO_CLEAR -p com.kscho.way
 */
class DemoDriveNavigator(private val real: Navigator, private val scope: CoroutineScope, private val clock: () -> Long) : Navigator {
  private data class Route(val path: List<LatLng>, val speedup: Int, val metersPerSecond: Double)

  @Volatile private var route: Route? = null
  @Volatile private var sim: SimulatedNavigator? = null
  private var forwarding: Job? = null
  private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
  override val events: SharedFlow<NavEvent> = _events.asSharedFlow()

  init {
    // 진짜 내비 소식은 시연 주행 중이 아닐 때만 그대로 넘긴다
    scope.launch { real.events.collect { if (sim == null) _events.emit(it) } }
  }

  /** 시연 경로 — 마지막 점이 픽업 위치 근처여야 쓴다([MATCH_METERS]). */
  fun setRoute(path: List<LatLng>, speedup: Int, metersPerSecond: Double) {
    require(path.size >= 2) { "경로는 점 두 개 이상" }
    route = Route(path, speedup.coerceIn(1, 60), metersPerSecond.coerceIn(1.0, 40.0))
  }

  fun clear() {
    route = null
  }

  /** 픽업 위치가 시연 경로 끝과 맞으면 그 경로를 달리는 모의 주행(끝점은 정확히 픽업 위치로). */
  private fun simFor(destination: LatLng): SimulatedNavigator? {
    val r = route ?: return null
    if (Geo.distanceMeters(r.path.last(), destination) > MATCH_METERS) return null
    val path = r.path + destination
    return SimulatedNavigator(scope, clock, start = path.first(), speedMetersPerSecond = r.metersPerSecond, timeScale = r.speedup, roadRoute = { _, _ -> path })
  }

  override suspend fun currentLocation(): LatLng? = sim?.currentLocation() ?: route?.path?.first() ?: real.currentLocation()

  override suspend fun preview(destination: LatLng, name: String): RoutePreview = simFor(destination)?.preview(destination, name) ?: real.preview(destination, name)

  override suspend fun startGuidance(destination: LatLng, name: String, address: String) {
    stopSim()
    val s = simFor(destination) ?: return real.startGuidance(destination, name, address)
    sim = s
    forwarding = scope.launch { s.events.collect { _events.emit(it) } }
    s.startGuidance(destination, name, address)
  }

  override fun stopGuidance() {
    stopSim()
    real.stopGuidance()
  }

  /** 탑승 뒤 목적지 — 진짜 Pleos 내비로(실제와 같게). */
  override suspend fun handOver(destination: LatLng, name: String, address: String) {
    stopSim()
    real.handOver(destination, name, address)
  }

  override fun openNavi() {
    stopSim()
    real.openNavi()
  }

  private fun stopSim() {
    forwarding?.cancel()
    forwarding = null
    sim?.stopGuidance()
    sim = null
  }

  private companion object {
    const val MATCH_METERS = 300.0
  }
}
