package com.kscho.way.data.repository

import com.kscho.way.data.contact.ContactDataSource
import com.kscho.way.data.remote.ArrivalRequest
import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.DriverStatusRequest
import com.kscho.way.data.remote.NotificationRequest
import com.kscho.way.data.remote.NotificationType
import com.kscho.way.data.remote.WayApi
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.MapFeatures
import com.kscho.way.domain.model.MyVehicle
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.PickupOutcome
import com.kscho.way.domain.model.PickupPlan
import com.kscho.way.domain.model.PickupRecord
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.StopRestriction
import com.kscho.way.domain.repository.ContactRepository
import com.kscho.way.domain.repository.CreatedPickup
import com.kscho.way.domain.repository.PickupHistoryRepository
import com.kscho.way.domain.repository.PickupRepository
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.repository.StopRestrictionRepository
import com.kscho.way.domain.repository.VehicleProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ContactRepositoryImpl(private val source: ContactDataSource) : ContactRepository {
  override suspend fun search(query: String): List<Contact> {
    val all = source.all().map { Contact(it.id, it.name, it.phone) }
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

  override suspend fun mapFeatures(center: LatLng, radiusMeters: Int): MapFeatures {
    val dto = api.mapFeatures(center.toDto(), radiusMeters)
    return MapFeatures(dto.roads.map { it.toDomain() }, dto.places.map { it.toDomain() }, dto.zones.map { it.toDomain() })
  }
}

class StopRestrictionRepositoryImpl(private val api: WayApi) : StopRestrictionRepository {
  override suspend fun check(location: LatLng, arrivalAtMillis: Long): StopRestriction {
    val dto = api.checkStopRestriction(location.toDto(), arrivalAtMillis)
    val zone = dto.zone
    return if (dto.restricted && zone != null) StopRestriction.NoStopping(zone.toDomain()) else StopRestriction.NotFound
  }
}

class VehicleProfileRepositoryImpl(private val api: WayApi) : VehicleProfileRepository {
  override suspend fun myVehicle(): MyVehicle = api.myVehicle().let { MyVehicle(it.description, it.plateNumber, it.driverName) }
}

class PickupRepositoryImpl(private val api: WayApi) : PickupRepository {
  override suspend fun create(plan: PickupPlan, arrivalAtMillis: Long): CreatedPickup {
    val point = plan.pickupPoint
    val response =
      api.createPickup(
        CreatePickupRequest(
          passengerName = plan.passenger.name,
          passengerPhone = plan.passenger.phone,
          placeName = plan.place?.name,
          pickupPoint = point.location.toDto(),
          pickupTitle = point.description.title,
          pickupDetail = point.description.detail,
          arrivalAtEpochMillis = arrivalAtMillis,
          vehicleDescription = plan.vehicle.description,
          plateNumber = plan.vehicle.plateNumber,
          driverName = plan.vehicle.driverName,
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

class PickupHistoryRepositoryImpl(private val api: WayApi) : PickupHistoryRepository {
  override suspend fun recent(limit: Int): List<PickupRecord> = api.recentPickups(limit).map { it.toDomain() }
}
