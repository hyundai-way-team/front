package com.kscho.way.vehicle

import android.content.Context
import com.kscho.way.domain.model.MyVehicle
import com.kscho.way.domain.repository.VehicleProfileRepository

/**
 * 운전자 이름 · 차량 설명 · 번호판 — 앱에 둔다(서버에 묻지 않는다).
 *
 * Pleos SDK 에는 번호판·차종을 주는 API 가 없다: CarInfo 는 VIN 뿐이고 getCRNInfo 는 VIN 으로 대체된 deprecated API,
 * 에뮬레이터에선 둘 다 "invalid"(11/11 확인). 그래서 운전자가 한 번 넣어 두는 값으로 둔다.
 * 입력 화면은 와이어프레임에 아직 없어, 저장값이 없으면 시연용 기본값을 쓴다.
 */
class LocalVehicleProfile(context: Context) : VehicleProfileRepository {
  private val prefs = context.applicationContext.getSharedPreferences("vehicle_profile", Context.MODE_PRIVATE)

  override suspend fun myVehicle(): MyVehicle =
    MyVehicle(
      description = prefs.getString(KEY_DESCRIPTION, null) ?: DEFAULT.description,
      plateNumber = prefs.getString(KEY_PLATE, null) ?: DEFAULT.plateNumber,
      driverName = prefs.getString(KEY_DRIVER, null) ?: DEFAULT.driverName,
    )

  /** 운전자가 내 차 정보를 바꿀 때(설정 화면이 생기면). */
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
