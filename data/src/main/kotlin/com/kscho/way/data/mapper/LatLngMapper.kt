package com.kscho.way.data.mapper

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.domain.model.geo.LatLng

internal fun LatLngDto.toDomain() = LatLng(lat, lng)

internal fun LatLng.toDto() = LatLngDto(latitude, longitude)
