package com.kscho.way.data.mapper

import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.domain.model.contact.CallNames
import com.kscho.way.domain.model.pickup.PickupOutcome
import com.kscho.way.domain.model.pickup.PickupPlan

internal fun PickupPlan.toCreatePickupRequest(arrivalAtMillis: Long) =
  CreatePickupRequest(
    passengerName = passenger.name,
    passengerCallName = passenger.callName,
    placeName = place?.name,
    pickupPoint = pickupPoint.location.toDto(),
    pickupTitle = pickupPoint.description.title,
    pickupDetail = pickupPoint.description.detail,
    arrivalAtEpochMillis = arrivalAtMillis,
    vehicleDescription = vehicle.description,
    plateNumber = vehicle.plateNumber,
    driverName = vehicle.driverName,
    driverCallName = CallNames.of(vehicle.driverName),
  )

internal fun PickupOutcome.toRequest() =
  CompletePickupRequest(
    boarded = boarded,
    boardedAtEpochMillis = boardedAtMillis,
    waitedSeconds = waitedSeconds,
    pickupLocationChanges = pickupLocationChanges,
    boardingSource = boardingSource?.name,
  )
