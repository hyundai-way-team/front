package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPointCheck
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.model.place.Place
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

@Singleton
class PickupDraft @Inject constructor() {
  val passenger = MutableStateFlow<Contact?>(null)
  val place = MutableStateFlow<Place?>(null)
  val pinStart = MutableStateFlow<LatLng?>(null)
  val check = MutableStateFlow<PickupPointCheck?>(null)
  val destination = MutableStateFlow<Place?>(null)

  fun reset() {
    passenger.value = null
    place.value = null
    pinStart.value = null
    check.value = null
    destination.value = null
  }

  fun reuse(record: PickupRecord) {
    passenger.value = record.passenger
    place.value = record.place
    pinStart.value = record.pickupPoint.location
    check.value = null
    destination.value = null
  }
}
