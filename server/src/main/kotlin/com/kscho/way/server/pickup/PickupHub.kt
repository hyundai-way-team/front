package com.kscho.way.server.pickup

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.mock.MockWorld
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.request.NotificationType
import com.kscho.way.data.response.CreatePickupResponse
import com.kscho.way.data.response.PassengerStatusResponse
import com.kscho.way.data.response.SharingWire
import com.kscho.way.data.response.WalkRouteResponse
import com.kscho.way.data.sms.PickupTexts
import com.kscho.way.server.sms.TextLog
import com.kscho.way.server.walk.OsmWalkRoutes
import com.kscho.way.server.walk.RoutePaths
import com.kscho.way.server.walk.WalkAnswer
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase {
  /** 오는 중 — P-03 대기 */
  EN_ROUTE,
  /** "곧 도착해요" 문자 보냄 — P-04 */
  ARRIVING_SOON,
  /** 정차, 1분 대기 타이머 — P-05 */
  ARRIVED,
  /** 탑승 — P-06 */
  BOARDED,
  /** 타지 않고 픽업을 접음 — P-06(링크 만료) */
  CLOSED;

  val ended: Boolean
    get() = this == BOARDED || this == CLOSED
}

sealed class PickupError(message: String) : RuntimeException(message) {
  class NotFound : PickupError("없는 픽업이거나 링크예요")

  /** 탑승·종료 뒤 — 링크 만료(기획 3-10) */
  class LinkExpired : PickupError("링크가 만료됐어요")

  /** 위치 공유를 허용하지 않았는데 위치가 옴 */
  class NotSharing : PickupError("위치 공유를 허용하지 않았어요")

  /** 열린 픽업이 너무 많음(인증 없는 공개 서버를 메모리로 채우지 못하게) */
  class Busy : PickupError("지금은 픽업을 더 만들 수 없어요. 잠시 뒤 다시 해 주세요")
}

/** 한 픽업의 지금 상태(바뀔 때마다 새로 만든다). */
data class PickupState(
  val id: String,
  val token: String,
  val request: CreatePickupRequest,
  val shareUrl: String,
  val phase: Phase = Phase.EN_ROUTE,
  /** 지금 기준 도착 예정 시각 — 출발 때 값에서 차량 보고·ETA 변경으로 바뀐다 */
  val arrivalAt: Long = request.arrivalAtEpochMillis,
  val driver: DriverStatusRequest? = null,
  val driverUpdatedAt: Long? = null,
  val etaChange: EtaChangeDto? = null,
  val timer: TimerDto? = null,
  val passenger: PassengerStatusResponse = PassengerStatusResponse(),
  /** 걷는 길을 마지막으로 물은 자리(찾는 중·못 찾음 포함) — 여기서 [PickupHub.WALK_RESTEP_METERS] 넘게 멀어져야 다시 묻는다 */
  val walkFrom: LatLngDto? = null,
  /** 지금 자리에선 걷는 길을 못 찾았다(공개 경로 서버가 못 찾음·너무 멂) — 웹은 '경로 안내'를 거둔다 */
  val walkFailed: Boolean = false,
  /** 경로 서버가 바빴다 — 이 시각이 지나면 움직이지 않아도 다시 묻는다 */
  val walkRetryAt: Long? = null,
  /** 차 → 픽업 위치 차 길(점을 줄인 것) — 웹엔 차가 지나온 부분을 잘라 보낸다 */
  val carRoute: List<LatLngDto>? = null,
  /** 차 길을 마지막으로 물은 자리·때(찾는 중·못 찾음 포함) */
  val carRouteFrom: LatLngDto? = null,
  val carRouteAskedAt: Long? = null,
  /** 경로 서버가 바빴다 — 이 시각이 지나면 다시 묻는다 */
  val carRouteRetryAt: Long? = null,
)

/**
 * 픽업 세션 — 운전자 앱(pickupId)과 동승자 웹(링크 토큰)이 같은 상태를 본다.
 * 서버는 참여 링크를 만들고 둘을 실시간으로 잇는다. 문자는 차가 운전자 번호로 보내고, 서버는 같은 문구([PickupTexts])를 [TextLog] 에 사본으로만 남긴다.
 *
 * 무엇을 언제 보낼지(ETA 재발송 기준·곧 도착 시점)는 운전자 앱의 PickupRules 가 정하고, 서버는 받은 대로 보낸다.
 * 앱에서 끝나는 일은 서버가 갖지 않는다: 동승자 번호(문자는 차가) · 지난 픽업 기록(앱 파일) · 부르는 이름(앱이 정해 보냄).
 */
class PickupHub(
  private val clock: () -> Long,
  private val texts: TextLog,
  /** 참여 링크 앞부분 — 동승자가 여는 주소(로컬 시연은 http://localhost:8080) */
  private val linkBase: String,
  private val newId: () -> String = ::randomToken,
  private val zone: ZoneId = MockWorld.ZONE,
  /** 만든 지 이만큼 지난 픽업은 지운다(링크도 그때 끝) — 지우지 않으면 공개 서버 메모리가 끝없이 찬다 */
  private val retentionMillis: Long = 12 * 60 * 60 * 1000L,
  /** 한꺼번에 둘 수 있는 픽업 수. 넘으면 새로 만들지 않는다 */
  private val maxPickups: Int = 2_000,
  /** 동승자 위치 → 픽업 위치 걷는 길(walk.OsmWalkRoutes — 무료 공개 보행 경로 서버). 없으면 경로 없이 돈다 */
  private val walkRoute: (suspend (from: LatLngDto, to: LatLngDto) -> WalkAnswer)? = null,
  /** 차 위치 → 픽업 위치 차 길(같은 공개 OSRM 의 차 프로필). 없으면 차 길 없이 돈다 */
  private val carRoute: (suspend (from: LatLngDto, to: LatLngDto) -> WalkAnswer)? = null,
  /** 걷는 길을 뒤에서 물을 곳 — 위치 보고 요청을 붙잡지 않게 */
  private val scope: CoroutineScope? = null,
) {
  private val created = ConcurrentHashMap<String, Long>()
  private val byId = ConcurrentHashMap<String, MutableStateFlow<PickupState>>()
  private val byToken = ConcurrentHashMap<String, MutableStateFlow<PickupState>>()
  private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

  // ── 운전자 앱

  /** 세션을 만들고 참여 링크를 돌려준다(기획 3-4). 차가 보낼 출발 문자는 사본으로 남긴다. */
  fun create(request: CreatePickupRequest): CreatePickupResponse {
    prune()
    if (byId.size >= maxPickups) throw PickupError.Busy()
    val id = "pk_" + newId()
    val token = newId()
    val url = "${linkBase.trimEnd('/')}/p/$token"
    val state = MutableStateFlow(PickupState(id, token, request, url))
    byId[id] = state
    byToken[token] = state
    created[id] = clock()
    texts.record(
      id,
      request.passengerName,
      PickupTexts.departure(
        driverName = request.driverName,
        vehicleDescription = request.vehicleDescription,
        plateNumber = request.plateNumber,
        pickupTitle = request.pickupTitle,
        arrivalAt = time(request.arrivalAtEpochMillis),
        link = url,
      ),
    )
    return CreatePickupResponse(id, url)
  }

  /** 운전자 앱이 받는 동승자 상태(링크 확인·위치 공유·위치). 지금 값부터, 바뀔 때만. */
  fun driverFeed(pickupId: String): Flow<PassengerStatusResponse> = pickup(pickupId).map { it.passenger }.distinctUntilChanged()

  fun passengerStatus(pickupId: String): PassengerStatusResponse = pickup(pickupId).value.passenger

  fun reportDriver(pickupId: String, request: DriverStatusRequest) {
    val state = pickup(pickupId)
    state.update { if (it.phase.ended) it else it.copy(driver = request, driverUpdatedAt = clock(), arrivalAt = request.arrivalAtEpochMillis) }
    request.location?.let { askCarRoute(state, it) }
  }

  /**
   * 차가 오는 길을 뒤에서 묻는다(동승자 웹 파란 선 — Pleos 내비는 경로 모양을 주지 않는다). 처음 한 번, 그리고 차가 길에서
   * [CAR_OFF_ROUTE_METERS] 넘게 벗어났을 때만 — 그것도 [CAR_ROUTE_MIN_INTERVAL_MILLIS] 에 한 번(무료 공개 경로 서버 사용 정책).
   * 길이 없다는 답은 기억해 차가 [CAR_RESTEP_METERS] 넘게 움직여야 다시 묻는다.
   */
  private fun askCarRoute(state: MutableStateFlow<PickupState>, here: LatLngDto) {
    val route = carRoute ?: return
    val scope = scope ?: return
    val now = state.value
    if (now.phase != Phase.EN_ROUTE && now.phase != Phase.ARRIVING_SOON) return
    val t = clock()
    val retryDue = now.carRouteRetryAt?.let { t >= it } ?: false
    val rested = now.carRouteAskedAt?.let { t - it >= CAR_ROUTE_MIN_INTERVAL_MILLIS } ?: true
    val due =
      when {
        now.carRouteRetryAt != null -> retryDue
        now.carRoute != null -> rested && RoutePaths.distanceToPath(here, now.carRoute) > CAR_OFF_ROUTE_METERS
        else -> now.carRouteFrom == null || (rested && OsmWalkRoutes.distanceMeters(now.carRouteFrom, here) >= CAR_RESTEP_METERS)
      }
    if (!due) return
    val pickup = now.request.pickupPoint
    state.update { it.copy(carRouteFrom = here, carRouteAskedAt = t, carRouteRetryAt = null) }
    scope.launch {
      val answer = runCatching { route(here, pickup) }.getOrElse { WalkAnswer.Unavailable }
      state.update {
        // 그새 다른 자리에서 다시 물었거나 차가 섰으면 버린다
        if (it.carRouteFrom != here || (it.phase != Phase.EN_ROUTE && it.phase != Phase.ARRIVING_SOON)) return@update it
        when (answer) {
          is WalkAnswer.Found -> it.copy(carRoute = RoutePaths.simplify(answer.route.path, CAR_ROUTE_TOLERANCE_METERS))
          WalkAnswer.NoRoute -> it.copy(carRoute = null)
          // 바쁨·오류는 답이 아니다 — 찾아 둔 길을 그대로 두고 잠시 뒤 다시 묻는다
          WalkAnswer.Unavailable -> it.copy(carRouteRetryAt = clock() + CAR_ROUTE_MIN_INTERVAL_MILLIS)
        }
      }
    }
  }

  fun notify(pickupId: String, request: NotificationRequest) {
    val state = pickup(pickupId)
    val current = state.value
    if (current.phase.ended) return
    when (request.type) {
      NotificationType.ETA_CHANGED -> {
        val before = request.previousArrivalAtEpochMillis ?: current.arrivalAt
        state.update { it.copy(etaChange = EtaChangeDto(before, request.arrivalAtEpochMillis), arrivalAt = request.arrivalAtEpochMillis) }
        texts.record(pickupId, current.request.passengerName, PickupTexts.etaChanged(time(before), time(request.arrivalAtEpochMillis), current.shareUrl))
      }
      NotificationType.ARRIVING_SOON -> {
        // 픽업 1건당 1번(기획 3-6) — 이미 받았거나 차가 섰으면 다시 안 남긴다
        if (current.phase != Phase.EN_ROUTE) return
        state.update { if (it.phase == Phase.EN_ROUTE) it.copy(phase = Phase.ARRIVING_SOON) else it }
        texts.record(pickupId, current.request.passengerName, PickupTexts.arrivingSoon(current.shareUrl))
      }
      else -> throw IllegalArgumentException("모르는 알림 ${request.type}")
    }
  }

  /** 정차 — 동승자 웹에 같은 1분 타이머. 운전자 앱 시계가 아닌 서버 시계로 맞춘다(웹은 serverNow 로 보정). */
  fun arrival(pickupId: String, request: ArrivalRequest) {
    val now = clock()
    pickup(pickupId).update {
      if (it.phase.ended || it.timer != null) it
      else it.copy(phase = Phase.ARRIVED, timer = TimerDto(now, now + request.waitSeconds * 1000L, request.waitSeconds))
    }
  }

  /**
   * 탑승(또는 접음) — 위치 공유 종료·링크 만료(기획 3-10). [request] 는 지표(기획 3-11: 탑승·대기·위치 변경)로만 쓴다.
   * 홈 '최근 픽업' 기록은 앱이 갖는다.
   */
  fun complete(pickupId: String, request: CompletePickupRequest) {
    pickup(pickupId).update {
      if (it.phase.ended) it
      else
        it.copy(
          phase = if (request.boarded) Phase.BOARDED else Phase.CLOSED,
          passenger = it.passenger.copy(sharing = SharingWire.ENDED, location = null, walk = null),
          walkFrom = null,
        )
    }
  }

  // ── 동승자 웹(링크 토큰)

  fun view(token: String): PassengerViewDto = link(token).value.toView()

  /** 동승자 웹 실시간 화면 상태 — 지금 값부터, 바뀔 때마다. */
  fun viewFeed(token: String): Flow<PassengerViewDto> = link(token).map { it.toView() }

  /** 링크를 열었다 — 운전자에게 "OO님이 픽업 위치를 확인했어요"(기획 3-8). */
  fun openLink(token: String) {
    link(token).update { if (it.phase.ended || it.passenger.linkOpened) it else it.copy(passenger = it.passenger.copy(linkOpened = true)) }
  }

  fun setSharing(token: String, allow: Boolean) {
    val state = link(token)
    if (state.value.phase.ended) throw PickupError.LinkExpired()
    state.update {
      val sharing = if (allow) SharingWire.SHARING else SharingWire.DECLINED
      it.copy(
        passenger =
          it.passenger.copy(
            linkOpened = true,
            sharing = sharing,
            location = if (allow) it.passenger.location else null,
            walk = if (allow) it.passenger.walk else null,
          ),
        walkFrom = if (allow) it.walkFrom else null,
        walkFailed = if (allow) it.walkFailed else false,
      )
    }
  }

  /** '늦어요'(와이어프레임 v2 P-03) — 운전자에게 "OO님이 조금 늦는대요". 한 번만 의미가 있다. */
  fun markLate(token: String) {
    val state = link(token)
    if (state.value.phase.ended) throw PickupError.LinkExpired()
    state.update { if (it.phase.ended || it.passenger.late) it else it.copy(passenger = it.passenger.copy(linkOpened = true, late = true)) }
  }

  fun updateLocation(token: String, location: LatLngDto) {
    val state = link(token)
    val now = state.value
    if (now.phase.ended) throw PickupError.LinkExpired()
    if (now.passenger.sharing != SharingWire.SHARING) throw PickupError.NotSharing()
    state.update {
      if (it.passenger.sharing != SharingWire.SHARING) it
      // 지난 경로의 출발점만 지금 자리로(그림이 동승자를 따라오게) — 새로 묻는 건 아래 askWalk
      else it.copy(passenger = it.passenger.copy(location = location, walk = it.passenger.walk?.startingAt(location)))
    }
    askWalk(state, location)
  }

  /**
   * 픽업 위치까지 걷는 길을 뒤에서 묻는다. 위치는 1~2초마다 오니 마지막으로 물은 자리에서 [WALK_RESTEP_METERS] 넘게
   * 움직였을 때만 묻는다(못 찾은 자리도 기억 — 공개 경로 서버에 1초마다 묻지 않게). 직선 [WALK_MAX_METERS] 넘게 떨어지면
   * 걷는 안내가 아니라서 묻지 않는다(아직 집·지하철에 있는 동승자).
   */
  private fun askWalk(state: MutableStateFlow<PickupState>, here: LatLngDto) {
    val route = walkRoute ?: return
    val scope = scope ?: return
    val now = state.value
    if (now.phase.ended || now.passenger.sharing != SharingWire.SHARING) return
    val from = now.walkFrom
    val retryDue = now.walkRetryAt?.let { clock() >= it } ?: false
    if (from != null && !retryDue && OsmWalkRoutes.distanceMeters(from, here) < WALK_RESTEP_METERS) return
    val pickup = now.request.pickupPoint
    if (OsmWalkRoutes.distanceMeters(here, pickup) > WALK_MAX_METERS) {
      state.update { it.copy(walkFrom = here, walkFailed = true, passenger = it.passenger.copy(walk = null)) }
      return
    }
    state.update { it.copy(walkFrom = here, walkRetryAt = null) }
    scope.launch {
      val answer = runCatching { route(here, pickup) }.getOrElse { WalkAnswer.Unavailable }
      state.update {
        // 그새 다른 자리에서 다시 물었거나 공유가 끝났으면 버린다
        if (it.walkFrom != here || it.phase.ended || it.passenger.sharing != SharingWire.SHARING) return@update it
        val at = it.passenger.location ?: here
        when (answer) {
          is WalkAnswer.Found -> it.copy(walkFailed = false, passenger = it.passenger.copy(walk = answer.route.startingAt(at)))
          WalkAnswer.NoRoute -> it.copy(walkFailed = true, passenger = it.passenger.copy(walk = null))
          // 바쁨·오류는 답이 아니다 — 찾아 둔 길을 그대로 두고 잠시 뒤 다시 묻는다
          WalkAnswer.Unavailable -> it.copy(walkRetryAt = clock() + WALK_RETRY_MILLIS)
        }
      }
    }
  }

  private fun WalkRouteResponse.startingAt(here: LatLngDto) = copy(path = listOf(here) + path.drop(1))

  /**
   * 웹에 보낼 차 길 — 차가 오는 중일 때만, 차가 지나온 부분을 잘라서. 차가 찾아 둔 길에서 벗어나 있으면(다른 길로 듦)
   * 옛 길로 곧은 선을 긋지 않게 새 길을 받을 때까지 감춘다.
   */
  private fun PickupState.carRouteView(): List<LatLngDto>? {
    val path = carRoute ?: return null
    if (phase != Phase.EN_ROUTE && phase != Phase.ARRIVING_SOON) return null
    val car = driver?.location ?: return path
    if (RoutePaths.distanceToPath(car, path) > CAR_OFF_ROUTE_METERS) return null
    return RoutePaths.remainingFrom(path, car)
  }

  private fun PickupState.toView(): PassengerViewDto {
    val r = request
    return PassengerViewDto(
      phase = phase.name,
      serverNow = clock(),
      driverName = r.driverName,
      driverCallName = r.driverCallName,
      passengerCallName = r.passengerCallName,
      vehicle = VehicleViewDto(r.vehicleDescription, r.plateNumber),
      pickup = PickupSpotDto(r.pickupPoint, r.pickupTitle, r.pickupDetail),
      arrivalAt = arrivalAt,
      car = driver?.let { CarDto(it.location, it.remainingSeconds, it.remainingMeters, driverUpdatedAt ?: clock()) },
      etaChange = etaChange,
      timer = timer,
      linkOpened = passenger.linkOpened,
      sharing = passenger.sharing,
      me = passenger.location,
      late = passenger.late,
      walk = passenger.walk,
      walkFailed = walkFailed,
      carRoute = carRouteView(),
    )
  }

  /** 오래된 픽업을 지운다(만든 뒤 [retentionMillis]). 만들 때마다 한 번 — 따로 도는 일꾼이 없다. */
  private fun prune() {
    val cutoff = clock() - retentionMillis
    for ((id, at) in created) {
      if (at >= cutoff) continue
      created.remove(id)
      byId.remove(id)?.let { byToken.remove(it.value.token) }
    }
  }

  private fun pickup(id: String) = byId[id] ?: throw PickupError.NotFound()

  private fun link(token: String) = byToken[token] ?: throw PickupError.NotFound()

  private fun time(epochMillis: Long) = hhmm.format(Instant.ofEpochMilli(epochMillis))

  companion object {
    private val random = SecureRandom()

    /** 걷는 길을 다시 묻는 거리 — 위치가 1초마다 와도 이만큼 움직여야 다시 묻는다(무료 공개 경로 서버 사용 정책). */
    const val WALK_RESTEP_METERS = 15.0
    /** 이보다 멀면 걷는 길을 묻지 않는다. */
    const val WALK_MAX_METERS = 3_000.0
    /** 경로 서버가 바쁘면 이만큼 뒤 다시 묻는다. */
    const val WALK_RETRY_MILLIS = 20_000L

    /** 차가 찾아 둔 길에서 이만큼 벗어나면 길을 다시 묻는다. */
    const val CAR_OFF_ROUTE_METERS = 40.0
    /** 차 길은 이 간격보다 자주 묻지 않는다(차 위치는 3초마다 온다). 벗어난 동안은 선이 감춰지니 너무 길지 않게. */
    const val CAR_ROUTE_MIN_INTERVAL_MILLIS = 10_000L
    /** 길이 없다는 답을 받은 뒤엔 차가 이만큼 움직여야 다시 묻는다. */
    const val CAR_RESTEP_METERS = 200.0
    /** 웹으로 자주 보내는 선이라 이보다 덜 휘는 점은 뺀다. */
    const val CAR_ROUTE_TOLERANCE_METERS = 3.0

    /** 링크에 쓰는 추측하기 어려운 값(16바이트, URL 에 안전한 글자). */
    fun randomToken(): String {
      val bytes = ByteArray(16).also(random::nextBytes)
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
  }
}
