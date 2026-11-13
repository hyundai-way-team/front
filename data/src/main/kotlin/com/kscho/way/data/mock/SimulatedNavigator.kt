package com.kscho.way.data.mock

import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.RoutePreview
import com.kscho.way.domain.vehicle.Navigator
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 시연용 주행. 실제 내비(requestRoute)는 Pleos 내비를 앞으로 띄워 Way 화면(D-06~D-10)이 가려지므로,
 * 화면 흐름을 보이려면 이걸 쓴다. 시간을 [timeScale] 배로 빨리 돌린다(도착 예정 '시각'은 그대로).
 *
 * 경로 앞쪽에서 교통 정체를 한 번 만나게 해 ETA 변경 문자(기획 3-5)도 보이게 한다.
 */
class SimulatedNavigator(
  private val scope: CoroutineScope,
  private val clock: () -> Long = System::currentTimeMillis,
  private val start: LatLng = MockWorld.CAR_START,
  /** 도심 평균 속도(m/s) — 4m/s ≈ 14km/h(신촌로 퇴근길) */
  private val speedMetersPerSecond: Double = 4.0,
  /** 실제 1초에 몇 초씩 갈지 */
  private val timeScale: Int = 20,
  private val tickMillis: Long = 1_000,
  /** 경로 이 비율쯤에서 정체를 만난다 — "곧 도착해요"(5분 전)보다 먼저 ETA 변경 문자가 나가게 앞쪽에 둔다 */
  private val trafficAtFraction: Double = 0.15,
  private val trafficDelaySeconds: Int = 4 * 60,
  /** 실제 도로를 따라가는 경로(없으면 null → ㄱ자). 시연 동네는 신촌로. */
  private val roadRoute: (LatLng, LatLng) -> List<LatLng>? = MockWorld::roadRoute,
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

  /** 탑승 뒤 목적지 안내 — 시연에선 목적지까지 달리지 않고 픽업이 끝난 것으로(차를 출발 지점으로). */
  override suspend fun handOver(destination: LatLng, name: String, address: String) = stopGuidance()

  /** 안내 종료(탑승·픽업 접기). 시연을 되풀이할 수 있게 차를 출발 지점으로 돌려놓는다. */
  override fun stopGuidance() {
    job?.cancel()
    job = null
    location = start
  }

  /** 신촌로를 따라가는 경로. 도로 밖이면 ㄱ자(동서 → 남북). */
  fun route(from: LatLng, to: LatLng): RoutePreview {
    val path = roadRoute(from, to) ?: listOf(from, LatLng(from.latitude, to.longitude), to).distinct()
    val meters = Geo.pathLengthMeters(path)
    return RoutePreview(meters.roundToInt(), (meters / speedMetersPerSecond).roundToInt(), path)
  }

  private suspend fun drive(route: RoutePreview) {
    val path = route.path.ifEmpty { listOf(location) }
    val total = Geo.pathLengthMeters(path)
    val startedAt = clock()
    var elapsed = 0.0 // 흉내 낸 시간(초)
    var travelled = 0.0
    var stuck = 0.0 // 남은 정체 시간(초)
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
      // 마지막 칸은 남은 거리만큼만 시간을 쓴다(도착 시각이 칸 단위로 밀리지 않게)
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
