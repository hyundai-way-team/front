package com.kscho.way.data.mock

import com.kscho.way.data.remote.ArrivalRequest
import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.CreatePickupResponse
import com.kscho.way.data.remote.DriverStatusRequest
import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.LocationDescriptionDto
import com.kscho.way.data.remote.NotificationRequest
import com.kscho.way.data.remote.NotificationType
import com.kscho.way.data.remote.PassengerStatusDto
import com.kscho.way.data.remote.PlaceDto
import com.kscho.way.data.remote.SharingWire
import com.kscho.way.data.remote.WayApi
import com.kscho.way.data.remote.ZoneDto
import com.kscho.way.data.sms.PickupTexts
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 동승자 흉내(웹 링크를 연 서연 님). 기본값은 와이어프레임 흐름이 다 보이게 맞춘 값. */
data class MockPassengerScenario(
  /** 문자를 받고 링크를 여는 데 걸리는 시간 */
  val opensLinkAfterMillis: Long = 4_000,
  /** false 면 위치 공유를 거부(운전자 화면에 "위치 미공유") */
  val sharesLocation: Boolean = true,
  /** 픽업 위치에서 북쪽(골목 안)으로 이만큼 떨어진 집에서 기다린다 */
  val homeDistanceMeters: Double = 130.0,
  /** "곧 도착해요" 문자(또는 차 정차)를 받으면 걷기 시작 — 일부러 조금 늦게 도착해 전화 연결 제안까지 보이게 */
  val walkSpeedMetersPerSecond: Double = 1.3,
  val updateIntervalMillis: Long = 1_000,
)

/** mock 서버가 남긴 문자 사본 — 실제 문자는 차가 운전자 번호로 보낸다(PassengerMessenger). 서버엔 번호가 없어 [to] 는 이름. */
data class MockSms(val pickupId: String, val to: String, val text: String, val atMillis: Long)

/**
 * 서버 없이 시연하기 위한 [WayApi]. 메모리에만 두고, 동승자 웹 쪽 행동(링크 열기·위치 공유·걸어오기)을 흉내 낸다.
 */
class MockWayApi(
  private val scope: CoroutineScope,
  private val clock: () -> Long = System::currentTimeMillis,
  private val scenario: MockPassengerScenario = MockPassengerScenario(),
  private val log: (String) -> Unit = {},
) : WayApi {
  private class Session(val id: String, val request: CreatePickupRequest, val url: String) {
    val status = MutableStateFlow(PassengerStatusDto())
    val leave = CompletableDeferred<Unit>()
    var simulation: Job? = null
    var arrivedAt: Long? = null
  }

  private val sessions = ConcurrentHashMap<String, Session>()
  private val counter = AtomicInteger(0)
  private val _messages = MutableStateFlow<List<MockSms>>(emptyList())

  /** 지금까지 보낸 문자(시연 확인용). */
  val messages: StateFlow<List<MockSms>> = _messages.asStateFlow()

  private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(MockWorld.ZONE)

  // ── 장소·지도

  override suspend fun searchPlaces(query: String): List<PlaceDto> {
    val q = query.normalized()
    if (q.isEmpty()) return emptyList()
    return MockWorld.places
      .filter { it.name.normalized().contains(q) || it.address.normalized().contains(q) }
      .sortedBy { if (it.name.normalized().startsWith(q)) 0 else 1 }
      .take(5)
      .map { PlaceDto(it.id, it.name, it.address, it.location.dto()) }
  }

  override suspend fun describeLocation(location: LatLngDto): LocationDescriptionDto {
    val p = location.domain()
    val landmark = MockWorld.landmarks.minByOrNull { Geo.distanceMeters(p, it.location) }
    val toRoad = minOf(Geo.distanceToPathMeters(p, MockWorld.SINCHON_RO_NORTH), Geo.distanceToPathMeters(p, MockWorld.SINCHON_RO_SOUTH))
    val nearRoad = toRoad <= 40.0
    val north = MockWorld.latitudeAt(MockWorld.SINCHON_RO_NORTH, p.longitude)
    val south = MockWorld.latitudeAt(MockWorld.SINCHON_RO_SOUTH, p.longitude)
    val side =
      when {
        north != null && p.latitude > north -> "북쪽"
        south != null && p.latitude < south -> "남쪽"
        else -> null // 두 차로 사이 = 도로 위
      }
    val title =
      when {
        landmark != null && Geo.distanceMeters(p, landmark.location) <= 18.0 -> landmark.title
        nearRoad && side == null -> "신촌로 도로 위"
        nearRoad -> "신촌로 도로 $side 길가"
        else -> "지도에서 고른 위치"
      }
    val detail =
      when {
        nearRoad && side == null -> "차로 한가운데예요 · 길가로 옮겨 주세요"
        nearRoad && side == "북쪽" && Geo.distanceMeters(p, MockWorld.VILLA_ALLEY_ENTRANCE) <= 30.0 -> "신촌로 · 도로 북쪽(빌라 쪽) 인도"
        nearRoad -> "신촌로 · 도로 $side 인도"
        else -> "큰길에서 떨어진 곳 · 골목 안일 수 있어요"
      }
    return LocationDescriptionDto(title, detail)
  }

  override suspend fun stopZones(center: LatLngDto, radiusMeters: Int): List<ZoneDto> {
    val c = center.domain()
    return MockWorld.zones.filter { z -> Geo.distanceToPathMeters(c, z.path) <= radiusMeters }.map { it.dto() }
  }

  // ── 픽업 세션

  override suspend fun createPickup(request: CreatePickupRequest): CreatePickupResponse {
    val n = counter.incrementAndGet()
    val id = "pickup-$n"
    val url = "https://way.mock/p/${id.hashCode().toUInt().toString(36)}"
    val session = Session(id, request, url)
    sessions[id] = session
    sms(
      session,
      PickupTexts.departure(
        driverName = request.driverName,
        vehicleDescription = request.vehicleDescription,
        plateNumber = request.plateNumber,
        pickupTitle = request.pickupTitle,
        arrivalAt = hhmm.format(Instant.ofEpochMilli(request.arrivalAtEpochMillis)),
        link = url,
      ),
    )
    session.simulation = scope.launch { simulatePassenger(session) }
    return CreatePickupResponse(id, url)
  }

  override fun passengerUpdates(pickupId: String): Flow<PassengerStatusDto> = sessions[pickupId]?.status ?: emptyFlow()

  override suspend fun reportDriver(pickupId: String, request: DriverStatusRequest) {
    log("차량 위치 보고 $pickupId 남은 ${request.remainingSeconds}s ${request.remainingMeters}m")
  }

  override suspend fun sendNotification(pickupId: String, request: NotificationRequest) {
    val session = sessions[pickupId] ?: return
    val eta = hhmm.format(Instant.ofEpochMilli(request.arrivalAtEpochMillis))
    when (request.type) {
      NotificationType.ETA_CHANGED -> {
        val before = request.previousArrivalAtEpochMillis?.let { hhmm.format(Instant.ofEpochMilli(it)) } ?: "?"
        sms(session, PickupTexts.etaChanged(before, eta, session.url))
      }
      NotificationType.ARRIVING_SOON -> {
        sms(session, PickupTexts.arrivingSoon(session.url))
        session.leave.complete(Unit)
      }
    }
  }

  override suspend fun reportArrival(pickupId: String, request: ArrivalRequest) {
    val session = sessions[pickupId] ?: return
    session.arrivedAt = request.arrivedAtEpochMillis
    session.leave.complete(Unit) // 차가 먼저 왔으면 그때라도 나온다
    log("정차 $pickupId — 동승자 웹에 ${request.waitSeconds}초 대기 타이머")
  }

  override suspend fun completePickup(pickupId: String, request: CompletePickupRequest) {
    val session = sessions.remove(pickupId) ?: return
    session.simulation?.cancel()
    session.status.update { it.copy(sharing = SharingWire.ENDED, location = null) }
    log("탑승 완료 $pickupId — 위치 공유 끝, 링크 만료 (탑승 ${request.boarded} · 대기 ${request.waitedSeconds}초)")
  }

  // ── 동승자 흉내

  private suspend fun simulatePassenger(session: Session) {
    delay(scenario.opensLinkAfterMillis)
    val pickup = session.request.pickupPoint.domain()
    if (!scenario.sharesLocation) {
      session.status.value = PassengerStatusDto(linkOpened = true, sharing = SharingWire.DECLINED)
      return
    }
    val home = Geo.offset(pickup, east = 0.0, north = scenario.homeDistanceMeters)
    session.status.value = PassengerStatusDto(linkOpened = true, sharing = SharingWire.SHARING, location = home.dto())
    session.leave.await()
    val path = listOf(home, pickup)
    val total = Geo.pathLengthMeters(path)
    var walked = 0.0
    while (walked < total) {
      delay(scenario.updateIntervalMillis)
      walked = min(total, walked + scenario.walkSpeedMetersPerSecond * scenario.updateIntervalMillis / 1000.0)
      val here = Geo.pointAlong(path, walked)
      session.status.update { it.copy(location = here.dto()) }
    }
  }

  private fun sms(session: Session, text: String) {
    val message = MockSms(session.id, session.request.passengerName, text, clock())
    _messages.update { it + message }
    log("문자 사본(차가 보냄) → ${session.request.passengerName}: $text")
  }
}

private fun String.normalized() = filterNot(Char::isWhitespace).lowercase()

internal fun LatLng.dto() = LatLngDto(latitude, longitude)

internal fun LatLngDto.domain() = LatLng(lat, lng)

private fun MockWorld.Zone.dto() =
  ZoneDto(id, name, rule, path.map { it.dto() }, radiusMeters, days?.sorted()?.map { it.name }, startMinute, endMinute)
