package com.kscho.way.server.pickup

import com.kscho.way.data.mock.MockWorld
import com.kscho.way.data.remote.ArrivalRequest
import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.CreatePickupResponse
import com.kscho.way.data.remote.DriverStatusRequest
import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.NotificationRequest
import com.kscho.way.data.remote.NotificationType
import com.kscho.way.data.remote.PassengerStatusDto
import com.kscho.way.data.remote.SharingWire
import com.kscho.way.data.sms.PickupTexts
import com.kscho.way.server.sms.TextLog
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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
  val passenger: PassengerStatusDto = PassengerStatusDto(),
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
  fun driverFeed(pickupId: String): Flow<PassengerStatusDto> = pickup(pickupId).map { it.passenger }.distinctUntilChanged()

  fun passengerStatus(pickupId: String): PassengerStatusDto = pickup(pickupId).value.passenger

  fun reportDriver(pickupId: String, request: DriverStatusRequest) {
    pickup(pickupId).update { if (it.phase.ended) it else it.copy(driver = request, driverUpdatedAt = clock(), arrivalAt = request.arrivalAtEpochMillis) }
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
          passenger = it.passenger.copy(sharing = SharingWire.ENDED, location = null),
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
      it.copy(passenger = it.passenger.copy(linkOpened = true, sharing = sharing, location = if (allow) it.passenger.location else null))
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
    state.update { if (it.passenger.sharing == SharingWire.SHARING) it.copy(passenger = it.passenger.copy(location = location)) else it }
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

    /** 링크에 쓰는 추측하기 어려운 값(16바이트, URL 에 안전한 글자). */
    fun randomToken(): String {
      val bytes = ByteArray(16).also(random::nextBytes)
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
  }
}
