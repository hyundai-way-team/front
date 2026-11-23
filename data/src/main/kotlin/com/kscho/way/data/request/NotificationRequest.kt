package com.kscho.way.data.request

import kotlinx.serialization.Serializable

@Serializable
data class NotificationRequest(val type: String, val arrivalAtEpochMillis: Long, val previousArrivalAtEpochMillis: Long? = null)
