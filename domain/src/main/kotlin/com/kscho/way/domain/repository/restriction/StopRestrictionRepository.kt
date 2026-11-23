package com.kscho.way.domain.repository.restriction

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.RestrictedZone
interface StopRestrictionRepository {
  suspend fun zonesNear(location: LatLng, radiusMeters: Int): List<RestrictedZone>
}
