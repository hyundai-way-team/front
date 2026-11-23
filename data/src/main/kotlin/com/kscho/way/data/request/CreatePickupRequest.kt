package com.kscho.way.data.request

import com.kscho.way.data.dto.LatLngDto
import kotlinx.serialization.Serializable

@Serializable
data class CreatePickupRequest(
  val passengerName: String,
  val passengerCallName: String,
  val placeName: String? = null,
  val pickupPoint: LatLngDto,
  val pickupTitle: String,
  val pickupDetail: String,
  val arrivalAtEpochMillis: Long,
  val vehicleDescription: String,
  val plateNumber: String,
  val driverName: String,
  val driverCallName: String,
)
