package com.kscho.way.domain.repository.pickup

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.pickup.CreatedPickup
import com.kscho.way.domain.model.pickup.PickupOutcome
import com.kscho.way.domain.model.pickup.PickupPlan
import kotlinx.coroutines.flow.Flow

interface PickupRepository {
  suspend fun create(plan: PickupPlan, arrivalAtMillis: Long): CreatedPickup

  fun passengerStatus(pickupId: String): Flow<PassengerStatus>

  suspend fun reportDriver(pickupId: String, location: LatLng?, remainingSeconds: Int, remainingMeters: Int, arrivalAtMillis: Long)

  suspend fun notifyEtaChanged(pickupId: String, previousArrivalAtMillis: Long, arrivalAtMillis: Long)

  suspend fun notifyArrivingSoon(pickupId: String, arrivalAtMillis: Long)

  suspend fun reportArrived(pickupId: String, arrivedAtMillis: Long, waitSeconds: Int)

  suspend fun complete(pickupId: String, outcome: PickupOutcome)
}
