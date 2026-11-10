package com.kscho.way.domain.model

/**
 * 운전자 차량과 운전자 이름. Pleos CarInfo 엔 번호판이 없어 서버(운전자 프로필)에서 받는다.
 * [driverName] 은 동승자 문자·웹에 "김도윤 님이 데리러 가고 있어요"로 나간다.
 */
data class MyVehicle(val description: String, val plateNumber: String, val driverName: String) {
  val label: String
    get() = "$description · $plateNumber"
}
