package com.kscho.way.data.remote

import kotlinx.serialization.Serializable

// 서버와 주고받는 모양(JSON). 시간은 epoch ms(UTC), 좌표는 WGS84.

@Serializable data class LatLngDto(val lat: Double, val lng: Double)

@Serializable data class PlaceDto(val id: String, val name: String, val address: String, val location: LatLngDto)

@Serializable data class LocationDescriptionDto(val title: String, val detail: String)

/**
 * 정차 금지 구간 데이터(GET /api/stop-zones) — 앱이 지도에 그리고, 정차 금지인지는 앱이 판단한다(도착 예정 시각 · 반경).
 * [days] 가 없으면 상시, 있으면 그 요일 [startMinute]~[endMinute](하루 중 분, 한국 시간)에만.
 */
@Serializable
data class ZoneDto(
  val id: String,
  val name: String,
  val rule: String,
  val path: List<LatLngDto>,
  val radiusMeters: Double = 10.0,
  /** MONDAY … SUNDAY */
  val days: List<String>? = null,
  val startMinute: Int? = null,
  val endMinute: Int? = null,
)

/**
 * 세션 만들기. 동승자 전화번호는 보내지 않는다 — 문자는 차가 보내고 기록은 앱에 두니 서버엔 필요 없다.
 * 부르는 이름(호칭)은 앱이 정해 보낸다(서버·웹은 그대로 쓴다).
 */
@Serializable
data class CreatePickupRequest(
  val passengerName: String,
  /** "이서연" → "서연" — 동승자 웹 "서연 님" */
  val passengerCallName: String,
  val placeName: String? = null,
  val pickupPoint: LatLngDto,
  val pickupTitle: String,
  val pickupDetail: String,
  val arrivalAtEpochMillis: Long,
  val vehicleDescription: String,
  val plateNumber: String,
  /** 문자·동승자 웹의 "김도윤 님이 데리러 가고 있어요" */
  val driverName: String,
  /** "김도윤" → "도윤" — 동승자 웹 "도윤 님의 차량 화면에만 표시돼요" */
  val driverCallName: String,
)

@Serializable data class CreatePickupResponse(val pickupId: String, val shareUrl: String)

object SharingWire {
  const val NOT_YET = "NOT_YET"
  const val SHARING = "SHARING"
  const val DECLINED = "DECLINED"
  const val ENDED = "ENDED"
}

@Serializable
data class PassengerStatusDto(
  val linkOpened: Boolean = false,
  val sharing: String = SharingWire.NOT_YET,
  val location: LatLngDto? = null,
  /** 동승자 웹 '늦어요'(와이어프레임 v2 P-03) */
  val late: Boolean = false,
)

@Serializable
data class DriverStatusRequest(val location: LatLngDto? = null, val remainingSeconds: Int, val remainingMeters: Int, val arrivalAtEpochMillis: Long)

object NotificationType {
  const val ETA_CHANGED = "ETA_CHANGED"
  const val ARRIVING_SOON = "ARRIVING_SOON"
}

@Serializable
data class NotificationRequest(val type: String, val arrivalAtEpochMillis: Long, val previousArrivalAtEpochMillis: Long? = null)

@Serializable data class ArrivalRequest(val arrivedAtEpochMillis: Long, val waitSeconds: Int)

@Serializable
data class CompletePickupRequest(
  val boarded: Boolean,
  val boardedAtEpochMillis: Long? = null,
  val waitedSeconds: Int? = null,
  val pickupLocationChanges: Int,
  /** DOOR · MANUAL · VOICE */
  val boardingSource: String? = null,
)

@Serializable data class ContactDto(val id: String, val name: String, val phone: String)
