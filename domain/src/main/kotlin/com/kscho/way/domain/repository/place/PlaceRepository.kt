package com.kscho.way.domain.repository.place

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
interface PlaceRepository {
  suspend fun search(query: String): List<Place>

  suspend fun describe(location: LatLng): LocationDescription
}
