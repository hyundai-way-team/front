package com.kscho.way.data.repository

import com.kscho.way.data.contact.ContactDataSource
import com.kscho.way.data.dataSource.base.apiCall
import com.kscho.way.data.remote.ArrivalRequest
import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.DriverStatusRequest
import com.kscho.way.data.remote.NotificationRequest
import com.kscho.way.data.remote.NotificationType
import com.kscho.way.data.remote.WayApi
import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.base.map
import com.kscho.way.domain.model.contact.CallNames
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.pickup.CreatedPickup
import com.kscho.way.domain.model.pickup.PickupOutcome
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.repository.contact.ContactRepository
import com.kscho.way.domain.repository.pickup.PickupRepository
import com.kscho.way.domain.repository.place.PlaceRepository
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

class StopRestrictionRepositoryImpl(private val api: WayApi) : StopRestrictionRepository {
  override suspend fun zonesNear(location: LatLng, radiusMeters: Int): ApiState<List<RestrictedZone>> =
    apiCall { api.stopZones(location.toDto(), radiusMeters).map { it.toDomain() } }
}

class PickupRepositoryImpl(private val api: WayApi) : PickupRepository {
  override suspend fun create(plan: PickupPlan, arrivalAtMillis: Long): ApiState<CreatedPickup> {
    val point = plan.pickupPoint
    return apiCall {
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
    }.map { CreatedPickup(it.pickupId, it.shareUrl) }
  }

  override fun passengerStatus(pickupId: String): Flow<PassengerStatus> =
    api.passengerUpdates(pickupId).map { it.toDomain() }.distinctUntilChanged()

  override suspend fun reportDriver(pickupId: String, location: LatLng?, remainingSeconds: Int, remainingMeters: Int, arrivalAtMillis: Long): ApiState<Unit> = apiCall {
    api.reportDriver(pickupId, DriverStatusRequest(location?.toDto(), remainingSeconds, remainingMeters, arrivalAtMillis))
  }

  override suspend fun notifyEtaChanged(pickupId: String, previousArrivalAtMillis: Long, arrivalAtMillis: Long): ApiState<Unit> = apiCall {
    api.sendNotification(pickupId, NotificationRequest(NotificationType.ETA_CHANGED, arrivalAtMillis, previousArrivalAtMillis))
  }

  override suspend fun notifyArrivingSoon(pickupId: String, arrivalAtMillis: Long): ApiState<Unit> = apiCall {
    api.sendNotification(pickupId, NotificationRequest(NotificationType.ARRIVING_SOON, arrivalAtMillis))
  }

  override suspend fun reportArrived(pickupId: String, arrivedAtMillis: Long, waitSeconds: Int): ApiState<Unit> = apiCall {
    api.reportArrival(pickupId, ArrivalRequest(arrivedAtMillis, waitSeconds))
  }

  override suspend fun complete(pickupId: String, outcome: PickupOutcome): ApiState<Unit> = apiCall {
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
