package com.kscho.way.data.repository.pickup

import com.kscho.way.data.dataSource.remote.PassengerSocketDataSource
import com.kscho.way.data.dataSource.remote.PickupRemoteDataSource
import com.kscho.way.data.mapper.toCreatePickupRequest
import com.kscho.way.data.mapper.toDomain
import com.kscho.way.data.mapper.toDto
import com.kscho.way.data.mapper.toRequest
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.request.NotificationType
import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.base.map
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.pickup.CreatedPickup
import com.kscho.way.domain.model.pickup.PickupOutcome
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.repository.pickup.PickupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PickupRepositoryImpl @Inject constructor(
  private val pickupRemoteDataSource: PickupRemoteDataSource,
  private val passengerSocketDataSource: PassengerSocketDataSource,
) : PickupRepository {
  override suspend fun create(plan: PickupPlan, arrivalAtMillis: Long): ApiState<CreatedPickup> =
    pickupRemoteDataSource.createPickup(plan.toCreatePickupRequest(arrivalAtMillis)).map { CreatedPickup(it.pickupId, it.shareUrl) }

  override fun passengerStatus(pickupId: String): Flow<PassengerStatus> =
    passengerSocketDataSource.passengerUpdates(pickupId).map { it.toDomain() }.distinctUntilChanged()

  override suspend fun reportDriver(
    pickupId: String,
    location: LatLng?,
    remainingSeconds: Int,
    remainingMeters: Int,
    arrivalAtMillis: Long,
  ): ApiState<Unit> =
    pickupRemoteDataSource.reportDriver(pickupId, DriverStatusRequest(location?.toDto(), remainingSeconds, remainingMeters, arrivalAtMillis))

  override suspend fun notifyEtaChanged(pickupId: String, previousArrivalAtMillis: Long, arrivalAtMillis: Long): ApiState<Unit> =
    pickupRemoteDataSource.sendNotification(pickupId, NotificationRequest(NotificationType.ETA_CHANGED, arrivalAtMillis, previousArrivalAtMillis))

  override suspend fun notifyArrivingSoon(pickupId: String, arrivalAtMillis: Long): ApiState<Unit> =
    pickupRemoteDataSource.sendNotification(pickupId, NotificationRequest(NotificationType.ARRIVING_SOON, arrivalAtMillis))

  override suspend fun reportArrived(pickupId: String, arrivedAtMillis: Long, waitSeconds: Int): ApiState<Unit> =
    pickupRemoteDataSource.reportArrival(pickupId, ArrivalRequest(arrivedAtMillis, waitSeconds))

  override suspend fun complete(pickupId: String, outcome: PickupOutcome): ApiState<Unit> =
    pickupRemoteDataSource.completePickup(pickupId, outcome.toRequest())
}
