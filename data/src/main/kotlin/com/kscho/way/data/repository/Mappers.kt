package com.kscho.way.data.repository

import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.LocationDescriptionDto
import com.kscho.way.data.remote.PassengerStatusDto
import com.kscho.way.data.remote.PlaceDto
import com.kscho.way.data.remote.SharingWire
import com.kscho.way.data.remote.ZoneDto
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.passenger.LocationSharing
import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.passenger.WalkRoute
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.model.restriction.ZoneSchedule
import java.time.DayOfWeek

internal fun LatLngDto.toDomain() = LatLng(lat, lng)

internal fun LatLng.toDto() = LatLngDto(latitude, longitude)

internal fun PlaceDto.toDomain() = Place(id, name, address, location.toDomain())

internal fun LocationDescriptionDto.toDomain() = LocationDescription(title, detail)

internal fun ZoneDto.toDomain(): RestrictedZone {
  val start = startMinute
  val end = endMinute
  val schedule =
    if (days != null && start != null && end != null) ZoneSchedule(days.mapNotNull { d -> DayOfWeek.entries.firstOrNull { it.name == d } }.toSet(), start, end)
    else null
  return RestrictedZone(id, name, rule, path.map { it.toDomain() }, radiusMeters, schedule)
}

internal fun PassengerStatusDto.toDomain() =
  PassengerStatus(
    linkOpened = linkOpened,
    sharing =
      when (sharing) {
        SharingWire.SHARING -> LocationSharing.SHARING
        SharingWire.DECLINED -> LocationSharing.DECLINED
        SharingWire.ENDED -> LocationSharing.ENDED
        else -> LocationSharing.NOT_YET
      },
    location = location?.toDomain(),
    late = late,
    walk = walk?.let { w -> WalkRoute(w.path.map { it.toDomain() }, w.meters, w.seconds) },
  )
