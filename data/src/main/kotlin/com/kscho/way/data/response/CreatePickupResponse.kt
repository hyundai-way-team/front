package com.kscho.way.data.response

import kotlinx.serialization.Serializable

@Serializable data class CreatePickupResponse(val pickupId: String, val shareUrl: String)
