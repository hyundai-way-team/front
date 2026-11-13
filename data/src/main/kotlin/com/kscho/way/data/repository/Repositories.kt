package com.kscho.way.data.repository

import com.kscho.way.data.contact.ContactDataSource
import com.kscho.way.data.remote.ArrivalRequest
import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.DriverStatusRequest
import com.kscho.way.data.remote.NotificationRequest
import com.kscho.way.data.remote.NotificationType
import com.kscho.way.data.remote.WayApi
import com.kscho.way.domain.model.CallNames
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.PickupOutcome
import com.kscho.way.domain.model.PickupPlan
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.repository.ContactRepository
import com.kscho.way.domain.repository.CreatedPickup
import com.kscho.way.domain.repository.PickupRepository
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.repository.StopRestrictionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 연락처 = 차에 연결된 휴대폰 연락처([source]) + 지난 픽업 동승자([recent]).
 * '최근 및 저장된 연락처' 순서: 최근에 태운 사람 먼저(연락처에 있으면 그 항목으로), 그다음 연락처 순서 그대로.
 * 같은 사람은 번호 숫자로 가린다(모양이 "010-0000-1234"·"01000001234"로 달라도 하나).
 */
class ContactRepositoryImpl(
  private val source: ContactDataSource,
  private val recent: suspend () -> List<Contact> = { emptyList() },
) : ContactRepository {
  override suspend fun search(query: String): List<Contact> {
    val device = source.all().map { Contact(it.id, it.name, it.phone) }
    val byDigits = device.associateBy { it.phone.filter(Char::isDigit) }
    val firsts = runCatching { recent() }.getOrDefault(emptyList()).map { byDigits[it.phone.filter(Char::isDigit)] ?: it }
    val all = (firsts + device).distinctBy { it.phone.filter(Char::isDigit) }
    val q = query.trim()
    if (q.isEmpty()) return all
    val digits = q.filter(Char::isDigit)
    return all.filter { c -> c.name.contains(q) || (digits.isNotEmpty() && c.phone.filter(Char::isDigit).contains(digits)) }
  }
}

class PlaceRepositoryImpl(private val api: WayApi) : PlaceRepository {
  override suspend fun search(query: String): List<Place> =
    if (query.isBlank()) emptyList() else api.searchPlaces(query.trim()).map { it.toDomain() }

  override suspend fun describe(location: LatLng): LocationDescription = api.describeLocation(location.toDto()).toDomain()
}

/** 정차 금지 구간 데이터(서버). 지도에 그리기·판단(StopRestrictionRules)은 앱이 한다. */
class StopRestrictionRepositoryImpl(private val api: WayApi) : StopRestrictionRepository {
  override suspend fun zonesNear(location: LatLng, radiusMeters: Int): List<RestrictedZone> =
    api.stopZones(location.toDto(), radiusMeters).map { it.toDomain() }
}

class PickupRepositoryImpl(private val api: WayApi) : PickupRepository {
  override suspend fun create(plan: PickupPlan, arrivalAtMillis: Long): CreatedPickup {
    val point = plan.pickupPoint
    val response =
      api.createPickup(
        CreatePickupRequest(
          passengerName = plan.passenger.name,
          passengerCallName = plan.passenger.callName,
          placeName = plan.place?.name,
          pickupPoint = point.location.toDto(),
          pickupTitle = point.description.title,
          pickupDetail = point.description.detail,
          arrivalAtEpochMillis = arrivalAtMillis,
          vehicleDescription = plan.vehicle.description,
          plateNumber = plan.vehicle.plateNumber,
          driverName = plan.vehicle.driverName,
          driverCallName = CallNames.of(plan.vehicle.driverName),
        )
      )
    return CreatedPickup(response.pickupId, response.shareUrl)
  }

  override fun passengerStatus(pickupId: String): Flow<PassengerStatus> =
    api.passengerUpdates(pickupId).map { it.toDomain() }.distinctUntilChanged()

  override suspend fun reportDriver(pickupId: String, location: LatLng?, remainingSeconds: Int, remainingMeters: Int, arrivalAtMillis: Long) {
    api.reportDriver(pickupId, DriverStatusRequest(location?.toDto(), remainingSeconds, remainingMeters, arrivalAtMillis))
  }

  override suspend fun notifyEtaChanged(pickupId: String, previousArrivalAtMillis: Long, arrivalAtMillis: Long) {
    api.sendNotification(pickupId, NotificationRequest(NotificationType.ETA_CHANGED, arrivalAtMillis, previousArrivalAtMillis))
  }

  override suspend fun notifyArrivingSoon(pickupId: String, arrivalAtMillis: Long) {
    api.sendNotification(pickupId, NotificationRequest(NotificationType.ARRIVING_SOON, arrivalAtMillis))
  }

  override suspend fun reportArrived(pickupId: String, arrivedAtMillis: Long, waitSeconds: Int) {
    api.reportArrival(pickupId, ArrivalRequest(arrivedAtMillis, waitSeconds))
  }

  override suspend fun complete(pickupId: String, outcome: PickupOutcome) {
    api.completePickup(
      pickupId,
      CompletePickupRequest(
        boarded = outcome.boarded,
        boardedAtEpochMillis = outcome.boardedAtMillis,
        waitedSeconds = outcome.waitedSeconds,
        pickupLocationChanges = outcome.pickupLocationChanges,
        boardingSource = outcome.boardingSource?.name,
      ),
    )
  }
}
