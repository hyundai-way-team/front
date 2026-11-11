package com.kscho.way.server.pickup

import com.kscho.way.data.remote.LatLngDto
import kotlinx.serialization.Serializable

// 동승자 웹이 받는 화면 상태(JSON). 시간은 epoch ms, 타이머는 서버 시계 기준(serverNow 로 웹 시계를 맞춘다).

@Serializable data class VehicleViewDto(val description: String, val plateNumber: String)

@Serializable data class PickupSpotDto(val location: LatLngDto, val title: String, val detail: String)

@Serializable
data class CarDto(val location: LatLngDto? = null, val remainingSeconds: Int, val remainingMeters: Int, val updatedAt: Long)

@Serializable data class EtaChangeDto(val from: Long, val to: Long)

@Serializable data class TimerDto(val startedAt: Long, val endsAt: Long, val waitSeconds: Int)

@Serializable
data class PassengerViewDto(
  /** EN_ROUTE(P-03 대기) · ARRIVING_SOON(P-04) · ARRIVED(P-05 타이머) · BOARDED · CLOSED(P-06 종료) */
  val phase: String,
  val serverNow: Long,
  val driverName: String,
  val driverCallName: String,
  val passengerCallName: String,
  val vehicle: VehicleViewDto,
  val pickup: PickupSpotDto,
  /** 지금 기준 도착 예정 시각 — 차가 보고한 값, 아직 없으면 출발 때 값 */
  val arrivalAt: Long,
  val car: CarDto? = null,
  /** 마지막 '도착 시간 변경' 문자 — P-03 배너 */
  val etaChange: EtaChangeDto? = null,
  val timer: TimerDto? = null,
  val linkOpened: Boolean,
  /** NOT_YET · SHARING · DECLINED · ENDED */
  val sharing: String,
  /** 서버가 받은 내 위치(공유 중일 때만) */
  val me: LatLngDto? = null,
  /** '늦어요'를 이미 보냈는지 */
  val late: Boolean = false,
)

@Serializable data class SharingRequest(val allow: Boolean)

@Serializable data class PassengerLocationRequest(val lat: Double, val lng: Double, val accuracyMeters: Double? = null)
