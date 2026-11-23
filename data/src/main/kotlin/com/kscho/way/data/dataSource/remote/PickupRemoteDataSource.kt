package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.response.CreatePickupResponse
import com.kscho.way.domain.model.base.ApiState

interface PickupRemoteDataSource {
  suspend fun createPickup(request: CreatePickupRequest): ApiState<CreatePickupResponse>

  suspend fun reportDriver(pickupId: String, request: DriverStatusRequest): ApiState<Unit>

  suspend fun sendNotification(pickupId: String, request: NotificationRequest): ApiState<Unit>

  suspend fun reportArrival(pickupId: String, request: ArrivalRequest): ApiState<Unit>

  suspend fun completePickup(pickupId: String, request: CompletePickupRequest): ApiState<Unit>
}
