package com.kscho.way.data.remote

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.response.CreatePickupResponse
import com.kscho.way.data.response.LocationDescriptionResponse
import com.kscho.way.data.response.PassengerStatusResponse
import com.kscho.way.data.response.PlaceResponse
import com.kscho.way.data.response.StopZoneResponse
import kotlinx.coroutines.flow.Flow

interface WayApi {
  suspend fun searchPlaces(query: String): List<PlaceResponse>

  suspend fun describeLocation(location: LatLngDto): LocationDescriptionResponse

  suspend fun stopZones(center: LatLngDto, radiusMeters: Int): List<StopZoneResponse>

  suspend fun createPickup(request: CreatePickupRequest): CreatePickupResponse

  fun passengerUpdates(pickupId: String): Flow<PassengerStatusResponse>

  suspend fun reportDriver(pickupId: String, request: DriverStatusRequest)

  suspend fun sendNotification(pickupId: String, request: NotificationRequest)

  suspend fun reportArrival(pickupId: String, request: ArrivalRequest)

  suspend fun completePickup(pickupId: String, request: CompletePickupRequest)
}
