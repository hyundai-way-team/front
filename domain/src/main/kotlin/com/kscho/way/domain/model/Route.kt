package com.kscho.way.domain.model

/** 출발 전 경로 미리보기. [path] 는 지도에 그릴 선(모르면 비어 있음). */
data class RoutePreview(val distanceMeters: Int, val durationSeconds: Int, val path: List<LatLng> = emptyList())

/** 내비 안내 중 들어오는 일. */
sealed interface NavEvent {
  /** 픽업 위치까지 남은 시간·거리. [arrivalAtMillis] = 도착 예정 시각(epoch ms). */
  data class Progress(val location: LatLng?, val remainingSeconds: Int, val remainingMeters: Int, val arrivalAtMillis: Long) : NavEvent

  /** 픽업 위치 도착·정차. */
  data class Arrived(val atMillis: Long) : NavEvent
}
