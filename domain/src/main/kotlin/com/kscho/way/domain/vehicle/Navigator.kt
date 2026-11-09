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

  /** 내비 목적지를 픽업 위치로 설정하고 안내를 시작한다. 이후 [events] 로 진행·도착이 온다. */
  suspend fun startGuidance(destination: LatLng, name: String, address: String)

  /** 안내 중 진행·도착. 구독하자마자 놓치지 않게 hot flow 로 둔다. */
  val events: SharedFlow<NavEvent>

  fun stopGuidance()
}
