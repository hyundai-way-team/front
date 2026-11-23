package com.kscho.way.data.request

import kotlinx.serialization.Serializable

@Serializable
data class CompletePickupRequest(
  val boarded: Boolean,
  val boardedAtEpochMillis: Long? = null,
  val waitedSeconds: Int? = null,
  val pickupLocationChanges: Int,
  val boardingSource: String? = null,
)
