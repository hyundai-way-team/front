package com.kscho.way.data.mapper

import com.kscho.way.data.response.PassengerStatusResponse
import com.kscho.way.data.response.SharingWire
import com.kscho.way.domain.model.passenger.LocationSharing
import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.passenger.WalkRoute

internal fun PassengerStatusResponse.toDomain() =
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
