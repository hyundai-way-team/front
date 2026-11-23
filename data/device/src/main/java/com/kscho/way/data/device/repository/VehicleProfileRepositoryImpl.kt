package com.kscho.way.data.device.repository

import android.content.Context
import com.kscho.way.domain.model.vehicle.MyVehicle
import com.kscho.way.domain.repository.vehicle.VehicleProfileRepository
class VehicleProfileRepositoryImpl(context: Context) : VehicleProfileRepository {
  private val prefs = context.applicationContext.getSharedPreferences("vehicle_profile", Context.MODE_PRIVATE)

  override suspend fun myVehicle(): MyVehicle =
    MyVehicle(
      description = prefs.getString(KEY_DESCRIPTION, null) ?: DEFAULT.description,
      plateNumber = prefs.getString(KEY_PLATE, null) ?: DEFAULT.plateNumber,
      driverName = prefs.getString(KEY_DRIVER, null) ?: DEFAULT.driverName,
    )

  fun save(vehicle: MyVehicle) {
    prefs.edit().putString(KEY_DESCRIPTION, vehicle.description).putString(KEY_PLATE, vehicle.plateNumber).putString(KEY_DRIVER, vehicle.driverName).apply()
  }

  private companion object {
    const val KEY_DESCRIPTION = "description"
    const val KEY_PLATE = "plate"
    const val KEY_DRIVER = "driver"
    val DEFAULT = MyVehicle(description = "흰색 아반떼", plateNumber = "12가 3456", driverName = "김도윤")
  }
}
