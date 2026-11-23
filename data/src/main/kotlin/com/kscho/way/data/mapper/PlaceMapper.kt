package com.kscho.way.data.mapper

import com.kscho.way.data.response.LocationDescriptionResponse
import com.kscho.way.data.response.PlaceResponse
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place

internal fun PlaceResponse.toDomain() = Place(id, name, address, location.toDomain())

internal fun LocationDescriptionResponse.toDomain() = LocationDescription(title, detail)
