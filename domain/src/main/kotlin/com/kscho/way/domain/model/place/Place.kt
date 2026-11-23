package com.kscho.way.domain.model.place

import com.kscho.way.domain.model.geo.LatLng

data class Place(val id: String, val name: String, val address: String, val location: LatLng)

data class LocationDescription(val title: String, val detail: String)
