package com.kscho.way.domain.model.message

import com.kscho.way.domain.model.vehicle.MyVehicle
sealed interface PassengerText {
  val link: String

  data class Departure(
    override val link: String,
    val driverName: String,
    val vehicle: MyVehicle,
    val pickupTitle: String,
    val arrivalAtMillis: Long,
  ) : PassengerText

  data class EtaChanged(override val link: String, val beforeMillis: Long, val afterMillis: Long) : PassengerText

  data class ArrivingSoon(override val link: String) : PassengerText
}
