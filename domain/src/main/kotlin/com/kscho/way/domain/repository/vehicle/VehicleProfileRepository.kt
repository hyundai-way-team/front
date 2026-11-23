package com.kscho.way.domain.repository.vehicle

import com.kscho.way.domain.model.vehicle.MyVehicle
interface VehicleProfileRepository {
  suspend fun myVehicle(): MyVehicle
}
