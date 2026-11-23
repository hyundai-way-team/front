package com.kscho.way.domain.model.pickup

import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.geo.MapArea
import com.kscho.way.domain.model.restriction.RestrictedZone

object PickupZones {
  const val RADIUS_METERS = 300

  fun around(pickup: LatLng, zones: List<RestrictedZone>, radiusMeters: Int = RADIUS_METERS): List<RestrictedZone> =
    zones.filter { Geo.distanceToPathMeters(pickup, it.path) - it.radiusMeters <= radiusMeters }

  fun areaFor(pickup: LatLng): MapArea = MapArea.around(pickup, RADIUS_METERS)

  fun needsReload(loaded: MapArea?, pickup: LatLng): Boolean = loaded?.covers(pickup, RADIUS_METERS) != true
}
