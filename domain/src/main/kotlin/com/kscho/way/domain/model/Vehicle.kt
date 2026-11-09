package com.kscho.way.domain.model

/** 운전자 차량. Pleos CarInfo 엔 번호판이 없어 서버(운전자 프로필)에서 받는다. */
data class MyVehicle(val description: String, val plateNumber: String) {
  val label: String
    get() = "$description · $plateNumber"
}
