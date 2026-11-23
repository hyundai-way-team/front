package com.kscho.way.data.request

import com.kscho.way.data.dto.LatLngDto
import kotlinx.serialization.Serializable

@Serializable
data class DriverStatusRequest(val location: LatLngDto? = null, val remainingSeconds: Int, val remainingMeters: Int, val arrivalAtEpochMillis: Long)
