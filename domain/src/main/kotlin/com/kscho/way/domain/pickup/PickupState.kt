package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.TurnInstruction

import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.PickupPlan
import kotlin.math.roundToInt

enum class PickupPhase {
  /** 픽업 위치로 가는 중(D-06 · D-07) */
  DRIVING,
  /** 정차 · 1분 대기(D-08 · D-09) */
  WAITING,
  /** 탑승 확인(D-10) · 5초 뒤 다음 목적지로 잇기(D-11) */
  BOARDED,
}

/** 진행 중인 픽업 1건. 화면·음성·Gleo 가 모두 이 값을 본다. */
data class PickupState(
  val id: String,
  val shareUrl: String,
  val plan: PickupPlan,
  val startedAtMillis: Long,
  /** 지금 도착 예정 시각(epoch ms). */
  val arrivalAtMillis: Long,
  val phase: PickupPhase = PickupPhase.DRIVING,
  val carLocation: LatLng? = null,
  val remainingSeconds: Int? = plan.route.durationSeconds,
  val remainingMeters: Int? = plan.route.distanceMeters,
  /** 동승자에게 마지막으로 알린 도착 예정 시각(처음엔 출발 문자의 ETA). */
  val notifiedArrivalAtMillis: Long = arrivalAtMillis,
  val etaChangeNotices: Int = 0,
  val passenger: PassengerStatus = PassengerStatus(),
  val arrivingSoonSent: Boolean = false,
  val arrivedAtMillis: Long? = null,
  val callOffered: Boolean = false,
  val boardedAtMillis: Long? = null,
  val boardingSource: BoardingSource? = null,
  val lastAnnouncement: SpokenAnnouncement? = null,
  /** 다음 회전 안내(주행 중에만). */
  val nextTurn: TurnInstruction? = null,
  /** D-11 '취소' — 탑승 뒤 다음으로 잇지 않고 D-10 에 남는다. */
  val nextCanceled: Boolean = false,
  /** 탑승한 자리에서 정해 둔 목적지까지 걸리는 초와 도착 예정 시각(D-11). 내비가 답하기 전엔 null. */
  val destinationTripSeconds: Int? = null,
  val destinationArrivalAtMillis: Long? = null,
) {
  val isBoarded: Boolean
    get() = phase == PickupPhase.BOARDED

  /** 대기 타이머 남은 시간(ms). 정차 전·탑승 후면 null. */
  fun waitRemainingMillis(nowMillis: Long): Long? {
    val arrived = arrivedAtMillis ?: return null
    if (phase != PickupPhase.WAITING) return null
    return (arrived + PickupPolicy.WAIT_SECONDS * 1000L - nowMillis).coerceAtLeast(0L)
  }

  /** D-11 다음으로 잇기까지 남은 시간(ms). 탑승 전이거나 '취소'했으면 null. */
  fun connectRemainingMillis(nowMillis: Long): Long? {
    val boarded = boardedAtMillis ?: return null
    if (!isBoarded || nextCanceled) return null
    return (boarded + PickupPolicy.CONNECT_AFTER_BOARDING_SECONDS * 1000L - nowMillis).coerceAtLeast(0L)
  }

  /** 동승자 ↔ 픽업 위치 거리(m). 위치를 모르면 null. */
  val passengerDistanceMeters: Int?
    get() = passenger.location?.let { Geo.distanceMeters(it, plan.pickupPoint.location).roundToInt() }

  /** 정차 후 탑승까지 걸린 초. */
  val waitedSeconds: Int?
    get() {
      val arrived = arrivedAtMillis ?: return null
      val boarded = boardedAtMillis ?: return null
      return ((boarded - arrived) / 1000).toInt().coerceAtLeast(0)
    }
}
