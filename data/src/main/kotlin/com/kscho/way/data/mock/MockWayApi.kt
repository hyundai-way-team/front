package com.kscho.way.data.mock

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.remote.WayApi
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.request.NotificationType
import com.kscho.way.data.response.CreatePickupResponse
import com.kscho.way.data.response.LocationDescriptionResponse
import com.kscho.way.data.response.PassengerStatusResponse
import com.kscho.way.data.response.PlaceResponse
import com.kscho.way.data.response.SharingWire
import com.kscho.way.data.response.StopZoneResponse
import com.kscho.way.data.sms.PickupTexts
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
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

data class MockPassengerScenario(
  val opensLinkAfterMillis: Long = 4_000,
  val sharesLocation: Boolean = true,
  val homeDistanceMeters: Double = 130.0,
  val walkSpeedMetersPerSecond: Double = 1.3,
  val updateIntervalMillis: Long = 1_000,
)

data class MockSms(val pickupId: String, val to: String, val text: String, val atMillis: Long)

class MockWayApi(
  private val scope: CoroutineScope,
  private val clock: () -> Long = System::currentTimeMillis,
  private val scenario: MockPassengerScenario = MockPassengerScenario(),
  private val log: (String) -> Unit = {},
) : WayApi {
  private class Session(val id: String, val request: CreatePickupRequest, val url: String) {
    val status = MutableStateFlow(PassengerStatusResponse())
    val leave = CompletableDeferred<Unit>()
    var simulation: Job? = null
    var arrivedAt: Long? = null
  }

  private val sessions = ConcurrentHashMap<String, Session>()
  private val counter = AtomicInteger(0)
  private val _messages = MutableStateFlow<List<MockSms>>(emptyList())

  val messages: StateFlow<List<MockSms>> = _messages.asStateFlow()

  private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(MockWorld.ZONE)

  override suspend fun searchPlaces(query: String): List<PlaceResponse> {
    val q = query.normalized()
    if (q.isEmpty()) return emptyList()
    return MockWorld.places
      .filter { it.name.normalized().contains(q) || it.address.normalized().contains(q) }
      .sortedBy { if (it.name.normalized().startsWith(q)) 0 else 1 }
      .take(5)
      .map { PlaceResponse(it.id, it.name, it.address, it.location.dto()) }
  }

  override suspend fun describeLocation(location: LatLngDto): LocationDescriptionResponse {
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
        else -> null
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
    return LocationDescriptionResponse(title, detail)
  }

  override suspend fun stopZones(center: LatLngDto, radiusMeters: Int): List<StopZoneResponse> {
    val c = center.domain()
    return MockWorld.zones.filter { z -> Geo.distanceToPathMeters(c, z.path) <= radiusMeters }.map { it.dto() }
  }

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

  override fun passengerUpdates(pickupId: String): Flow<PassengerStatusResponse> = sessions[pickupId]?.status ?: emptyFlow()

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
    session.leave.complete(Unit)
    log("정차 $pickupId — 동승자 웹에 ${request.waitSeconds}초 대기 타이머")
  }

  override suspend fun completePickup(pickupId: String, request: CompletePickupRequest) {
    val session = sessions.remove(pickupId) ?: return
    session.simulation?.cancel()
    session.status.update { it.copy(sharing = SharingWire.ENDED, location = null) }
    log("탑승 완료 $pickupId — 위치 공유 끝, 링크 만료 (탑승 ${request.boarded} · 대기 ${request.waitedSeconds}초)")
  }

  private suspend fun simulatePassenger(session: Session) {
    delay(scenario.opensLinkAfterMillis)
    val pickup = session.request.pickupPoint.domain()
    if (!scenario.sharesLocation) {
      session.status.value = PassengerStatusResponse(linkOpened = true, sharing = SharingWire.DECLINED)
      return
    }
    val home = Geo.offset(pickup, east = 0.0, north = scenario.homeDistanceMeters)
    session.status.value = PassengerStatusResponse(linkOpened = true, sharing = SharingWire.SHARING, location = home.dto())
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
  StopZoneResponse(id, name, rule, path.map { it.dto() }, radiusMeters, days?.sorted()?.map { it.name }, startMinute, endMinute)
