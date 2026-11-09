package com.kscho.way.data.repository

import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.LocationDescriptionDto
import com.kscho.way.data.remote.PassengerStatusDto
import com.kscho.way.data.remote.PickupRecordDto
import com.kscho.way.data.remote.PlaceDto
import com.kscho.way.data.remote.RoadDto
import com.kscho.way.data.remote.SharingWire
import com.kscho.way.data.remote.ZoneDto
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.LocationSharing
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.PickupPoint
import com.kscho.way.domain.model.PickupRecord
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.model.Road

internal fun LatLngDto.toDomain() = LatLng(lat, lng)

internal fun LatLng.toDto() = LatLngDto(latitude, longitude)

internal fun PlaceDto.toDomain() = Place(id, name, address, location.toDomain())

internal fun LocationDescriptionDto.toDomain() = LocationDescription(title, detail)

internal fun RoadDto.toDomain() = Road(name, path.map { it.toDomain() })

internal fun ZoneDto.toDomain() = RestrictedZone(id, name, rule, path.map { it.toDomain() })

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
  )

internal fun PickupRecordDto.toDomain() =
  PickupRecord(
    id = id,
    passenger = Contact(id = passengerPhone.filter(Char::isDigit), name = passengerName, phone = passengerPhone),
    place = place?.toDomain(),
    pickupPoint = PickupPoint(pickupPoint.toDomain(), LocationDescription(pickupTitle, pickupDetail), distanceFromPlaceMeters),
    boarded = boarded,
    pickupLocationChanges = pickupLocationChanges,
    waitedSeconds = waitedSeconds,
    endedAtMillis = endedAtEpochMillis,
  )
