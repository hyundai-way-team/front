package com.kscho.way.domain.model.vehicle

data class MyVehicle(val description: String, val plateNumber: String, val driverName: String) {
  val label: String
    get() = "$description · $plateNumber"
}
