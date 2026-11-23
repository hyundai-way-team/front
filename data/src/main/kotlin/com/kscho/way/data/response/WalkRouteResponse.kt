package com.kscho.way.data.response

import com.kscho.way.data.dto.LatLngDto
import kotlinx.serialization.Serializable

@Serializable data class WalkRouteResponse(val path: List<LatLngDto>, val meters: Int, val seconds: Int)
