package com.kscho.way.data.device.vehicle

import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.route.NavEvent
import kotlin.math.roundToInt

internal object PickupTrack {
  const val POLL_MILLIS = 3_000L
  const val TRIP_REFRESH_MILLIS = 15_000L
  const val ARRIVE_METERS = 35.0

  data class Trip(val meters: Int, val seconds: Int, val from: LatLng?)

  fun progress(here: LatLng?, destination: LatLng, trip: Trip?, now: Long): NavEvent.Progress? {
    if (here == null && trip == null) return null
    val (meters, seconds) =
      when {
        trip != null -> {
          val ratio =
            if (here != null && trip.from != null) {
              val then = Geo.distanceMeters(trip.from, destination).coerceAtLeast(1.0)
              (Geo.distanceMeters(here, destination) / then).coerceIn(0.0, 1.5)
            } else {
              1.0
            }
          (trip.meters * ratio).roundToInt() to (trip.seconds * ratio).roundToInt()
        }
        else -> {
          val m = Geo.distanceMeters(here!!, destination) * 1.3
          m.roundToInt() to (m / 7.0).roundToInt()
        }
      }
    return NavEvent.Progress(here, seconds, meters, now + seconds * 1000L)
  }

  fun arrived(here: LatLng?, destination: LatLng): Boolean = here != null && Geo.distanceMeters(here, destination) <= ARRIVE_METERS
}
