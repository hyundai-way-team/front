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

class DemoDriveNavigator(private val real: Navigator, private val scope: CoroutineScope, private val clock: () -> Long) : Navigator {
  private data class Route(val path: List<LatLng>, val speedup: Int, val metersPerSecond: Double)

  @Volatile private var route: Route? = null
  @Volatile private var sim: SimulatedNavigator? = null
  private var forwarding: Job? = null
  private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
  override val events: SharedFlow<NavEvent> = _events.asSharedFlow()

  init {
    scope.launch { real.events.collect { if (sim == null) _events.emit(it) } }
  }

  fun setRoute(path: List<LatLng>, speedup: Int, metersPerSecond: Double) {
    require(path.size >= 2) { "경로는 점 두 개 이상" }
    route = Route(path, speedup.coerceIn(1, 60), metersPerSecond.coerceIn(1.0, 40.0))
  }

  fun clear() {
    route = null
  }

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
