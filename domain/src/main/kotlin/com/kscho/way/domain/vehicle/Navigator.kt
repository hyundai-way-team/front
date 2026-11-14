package com.kscho.way.domain.vehicle

import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.RoutePreview
import kotlinx.coroutines.flow.SharedFlow

/** 차량 내비(Pleos NaviHelper) 또는 시연용 시뮬레이션. */
interface Navigator {
  suspend fun currentLocation(): LatLng?

  /** 출발 전 경로 미리보기(거리·시간). */
  suspend fun preview(destination: LatLng, name: String): RoutePreview

  /**
   * 출발: 픽업 위치까지 따라가기 시작한다. 이후 [events] 로 진행·도착이 온다.
   * 차량 내비 화면은 띄우지 않는다 — 픽업까지는 Way 화면을 보고, 내비로는 탑승한 뒤에야 넘어간다([handOver]·[openNavi]).
   */
  suspend fun startGuidance(destination: LatLng, name: String, address: String)

  /** 안내 중 진행·도착. 구독하자마자 놓치지 않게 hot flow 로 둔다. */
  val events: SharedFlow<NavEvent>

  fun stopGuidance()

  /**
   * 탑승 뒤 목적지 안내를 내비에 넘긴다(D-05 목적지). 안내만 시작하고 Way 는 더 따라가지 않는다 —
   * 픽업 구간의 위치 묻기를 멈추고, 이 안내는 이후 [stopGuidance] 로 끄지 않는다(운전자의 안내).
   */
  suspend fun handOver(destination: LatLng, name: String, address: String)

  /** 목적지 없이 탑승을 마쳤을 때(D-11): 기본 내비 화면을 띄운다 — 운전자가 음성으로 목적지를 정하게. */
  fun openNavi() {}
}
