package com.kscho.way.domain.pickup

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
  /** 탑승 확인 · 안내 종료(D-10) */
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
) {
  val isBoarded: Boolean
    get() = phase == PickupPhase.BOARDED

  /** 대기 타이머 남은 시간(ms). 정차 전·탑승 후면 null. */
  fun waitRemainingMillis(nowMillis: Long): Long? {
    val arrived = arrivedAtMillis ?: return null
    if (phase != PickupPhase.WAITING) return null
    return (arrived + PickupPolicy.WAIT_SECONDS * 1000L - nowMillis).coerceAtLeast(0L)
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
