package com.kscho.way.pickup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/** 진행 중인 픽업 1건 (MVP: 동승자 1명). */
data class PickupSession(
  val passengerName: String,
  /** 동승자 전화번호. 차량 밖(Gleo 응답 등)으로 내보내지 않는다. */
  val passengerPhone: String,
  val pickupPlaceName: String,
  /** 픽업 위치까지 남은 시간(초). 내비 onDrivingInfo 로 갱신, 아직 모르면 null. */
  val remainingSeconds: Int?,
  /** 동승자가 픽업 링크를 열어 위치를 확인했는지. */
  val passengerCheckedLink: Boolean,
  /** 동승자가 위치 공유를 허용했는지. */
  val passengerSharingLocation: Boolean,
  /** 탑승 완료(도어 이벤트·Gleo '탑승 완료'). */
  val boarded: Boolean = false,
)

/**
 * 앱 전체가 보는 현재 픽업 상태. 화면·Gleo 수신기·차량 이벤트가 같은 값을 본다.
 * 서버 연동 전(11/15 시연)에는 mock 데이터가 여기로 들어온다.
 */
object PickupSessionStore {
  private val _session = MutableStateFlow<PickupSession?>(null)
  val session: StateFlow<PickupSession?> = _session.asStateFlow()

  fun start(session: PickupSession) {
    _session.value = session
  }

  /** 진행 중인 픽업이 있을 때만 바꾼다. */
  fun update(transform: (PickupSession) -> PickupSession) {
    _session.update { current -> current?.let(transform) }
  }

  /** 탑승 완료로 표시하고, 바꾸기 전 상태를 돌려준다(없으면 null). */
  fun markBoarded(): PickupSession? = _session.getAndUpdate { it?.copy(boarded = true) }

  fun clear() {
    _session.value = null
  }
}
