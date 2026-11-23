package com.kscho.way.data.api

import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.response.CreatePickupResponse
import com.kscho.way.data.response.LocationDescriptionResponse
import com.kscho.way.data.response.PlaceResponse
import com.kscho.way.data.response.StopZoneResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface WayApi {
  @GET("places")
  suspend fun searchPlaces(@Query("query") query: String): List<PlaceResponse>

  @GET("places/reverse")
  suspend fun describeLocation(@Query("lat") lat: Double, @Query("lng") lng: Double): LocationDescriptionResponse

  @GET("stop-zones")
  suspend fun getStopZones(
    @Query("lat") lat: Double,
    @Query("lng") lng: Double,
    @Query("radius") radiusMeters: Int,
  ): List<StopZoneResponse>

  @POST("pickups")
  suspend fun createPickup(@Body request: CreatePickupRequest): CreatePickupResponse

  @POST("pickups/{pickupId}/driver-status")
  suspend fun reportDriver(@Path("pickupId") pickupId: String, @Body request: DriverStatusRequest)

  @POST("pickups/{pickupId}/notifications")
  suspend fun sendNotification(@Path("pickupId") pickupId: String, @Body request: NotificationRequest)

  @POST("pickups/{pickupId}/arrival")
  suspend fun reportArrival(@Path("pickupId") pickupId: String, @Body request: ArrivalRequest)

  @POST("pickups/{pickupId}/complete")
  suspend fun completePickup(@Path("pickupId") pickupId: String, @Body request: CompletePickupRequest)
}
