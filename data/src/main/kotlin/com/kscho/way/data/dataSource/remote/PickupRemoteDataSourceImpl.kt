package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.api.WayApi
import com.kscho.way.data.dataSource.base.apiCall
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.response.CreatePickupResponse
import com.kscho.way.domain.model.base.ApiState
import javax.inject.Inject

class PickupRemoteDataSourceImpl @Inject constructor(
  private val wayApi: WayApi,
) : PickupRemoteDataSource {
  override suspend fun createPickup(request: CreatePickupRequest): ApiState<CreatePickupResponse> =
    apiCall { wayApi.createPickup(request) }

  override suspend fun reportDriver(pickupId: String, request: DriverStatusRequest): ApiState<Unit> =
    apiCall { wayApi.reportDriver(pickupId, request) }

  override suspend fun sendNotification(pickupId: String, request: NotificationRequest): ApiState<Unit> =
    apiCall { wayApi.sendNotification(pickupId, request) }

  override suspend fun reportArrival(pickupId: String, request: ArrivalRequest): ApiState<Unit> =
    apiCall { wayApi.reportArrival(pickupId, request) }

  override suspend fun completePickup(pickupId: String, request: CompletePickupRequest): ApiState<Unit> =
    apiCall { wayApi.completePickup(pickupId, request) }
}
