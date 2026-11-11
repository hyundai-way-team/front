package com.kscho.way.data.remote

import kotlinx.serialization.Serializable

// 서버와 주고받는 모양(JSON). 시간은 epoch ms(UTC), 좌표는 WGS84.

@Serializable data class LatLngDto(val lat: Double, val lng: Double)

@Serializable data class PlaceDto(val id: String, val name: String, val address: String, val location: LatLngDto)

@Serializable data class LocationDescriptionDto(val title: String, val detail: String)

@Serializable data class RoadDto(val name: String, val path: List<LatLngDto>)

@Serializable data class ZoneDto(val id: String, val name: String, val rule: String, val path: List<LatLngDto>)

@Serializable data class MapFeaturesDto(val roads: List<RoadDto>, val places: List<PlaceDto>, val zones: List<ZoneDto>)

@Serializable data class StopRestrictionDto(val restricted: Boolean, val zone: ZoneDto? = null)

/** 운전자 프로필 — 차량 설명·번호판 + 동승자 웹·문자에 나오는 운전자 이름("김도윤" → "도윤 님"). */
@Serializable data class VehicleDto(val description: String, val plateNumber: String, val driverName: String)

@Serializable
data class CreatePickupRequest(
  val passengerName: String,
  val passengerPhone: String,
  val placeName: String? = null,
  val pickupPoint: LatLngDto,
  val pickupTitle: String,
  val pickupDetail: String,
  val arrivalAtEpochMillis: Long,
  val vehicleDescription: String,
  val plateNumber: String,
  /** 문자·동승자 웹의 "김도윤 님이 데리러 가고 있어요" */
  val driverName: String,
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

@Serializable
data class PickupRecordDto(
  val id: String,
  val passengerName: String,
  val passengerPhone: String,
  val place: PlaceDto? = null,
  val pickupPoint: LatLngDto,
  val pickupTitle: String,
  val pickupDetail: String,
  val distanceFromPlaceMeters: Int? = null,
  val boarded: Boolean,
  val pickupLocationChanges: Int,
  val waitedSeconds: Int? = null,
  val endedAtEpochMillis: Long,
)

@Serializable data class ContactDto(val id: String, val name: String, val phone: String)
