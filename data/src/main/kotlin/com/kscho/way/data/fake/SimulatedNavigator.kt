package com.kscho.way.data.fake

import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.route.NavEvent
import com.kscho.way.domain.model.route.RoutePreview
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SimulatedNavigator(
  private val scope: CoroutineScope,
  private val clock: () -> Long = System::currentTimeMillis,
  private val start: LatLng = FakeWorld.CAR_START,
  private val speedMetersPerSecond: Double = 4.0,
  private val timeScale: Int = 20,
  private val tickMillis: Long = 1_000,
  private val trafficAtFraction: Double = 0.15,
  private val trafficDelaySeconds: Int = 4 * 60,
  private val roadRoute: (LatLng, LatLng) -> List<LatLng>? = FakeWorld::roadRoute,
) : Navigator {
  private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
  override val events: SharedFlow<NavEvent> = _events.asSharedFlow()

  @Volatile private var location: LatLng = start
  private var job: Job? = null

  override suspend fun currentLocation(): LatLng = location

  override suspend fun preview(destination: LatLng, name: String): RoutePreview = route(location, destination)

  override suspend fun startGuidance(destination: LatLng, name: String, address: String) {
    job?.cancel()
    val route = route(location, destination)
    job = scope.launch { drive(route) }
  }

  override suspend fun handOver(destination: LatLng, name: String, address: String) = stopGuidance()

  override fun stopGuidance() {
    job?.cancel()
    job = null
    location = start
  }

  fun route(from: LatLng, to: LatLng): RoutePreview {
    val path = roadRoute(from, to) ?: listOf(from, LatLng(from.latitude, to.longitude), to).distinct()
    val meters = Geo.pathLengthMeters(path)
    return RoutePreview(meters.roundToInt(), (meters / speedMetersPerSecond).roundToInt(), path)
  }

  private suspend fun drive(route: RoutePreview) {
    val path = route.path.ifEmpty { listOf(location) }
    val total = Geo.pathLengthMeters(path)
    val startedAt = clock()
    var elapsed = 0.0
    var travelled = 0.0
    var stuck = 0.0
    var trafficMet = false
    while (true) {
      delay(tickMillis)
      val step = tickMillis / 1000.0 * timeScale
      if (!trafficMet && travelled >= total * trafficAtFraction) {
        trafficMet = true
        stuck = trafficDelaySeconds.toDouble()
      }
      val stopped = min(stuck, step)
      stuck -= stopped
      val moving = min(step - stopped, (total - travelled) / speedMetersPerSecond)
      travelled = min(total, travelled + moving * speedMetersPerSecond)
      elapsed += stopped + moving
      location = Geo.pointAlong(path, travelled)
      val remainingMeters = total - travelled
      val remainingSeconds = remainingMeters / speedMetersPerSecond + stuck
      val arrivalAt = startedAt + ((elapsed + remainingSeconds) * 1000).toLong()
      _events.emit(NavEvent.Progress(location, remainingSeconds.roundToInt(), remainingMeters.roundToInt(), arrivalAt))
      if (remainingMeters < 1.0) {
        _events.emit(NavEvent.Arrived(clock()))
        return
      }
    }
  }
}
