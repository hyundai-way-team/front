package com.kscho.way.data.response

import com.kscho.way.data.dto.LatLngDto
import kotlinx.serialization.Serializable

@Serializable data class PlaceResponse(val id: String, val name: String, val address: String, val location: LatLngDto)
