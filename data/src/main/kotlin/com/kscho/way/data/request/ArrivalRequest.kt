package com.kscho.way.data.request

import kotlinx.serialization.Serializable

@Serializable data class ArrivalRequest(val arrivedAtEpochMillis: Long, val waitSeconds: Int)
