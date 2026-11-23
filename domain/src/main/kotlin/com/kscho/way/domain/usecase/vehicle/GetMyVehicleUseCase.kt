package com.kscho.way.domain.usecase.vehicle

import com.kscho.way.domain.model.vehicle.MyVehicle
import com.kscho.way.domain.repository.vehicle.VehicleProfileRepository
import javax.inject.Inject

class GetMyVehicleUseCase @Inject constructor(
  private val vehicleProfileRepository: VehicleProfileRepository,
) {
  suspend operator fun invoke(): MyVehicle = vehicleProfileRepository.myVehicle()
}
