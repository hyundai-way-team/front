package com.kscho.way.domain.model.route

import com.kscho.way.domain.model.geo.LatLng

data class RoutePreview(val distanceMeters: Int, val durationSeconds: Int, val path: List<LatLng> = emptyList())

sealed interface NavEvent {
  data class Progress(val location: LatLng?, val remainingSeconds: Int, val remainingMeters: Int, val arrivalAtMillis: Long) : NavEvent

  data class Arrived(val atMillis: Long) : NavEvent

  data class Turn(val instruction: TurnInstruction) : NavEvent
}

data class TurnInstruction(val kind: TurnKind, val distanceMeters: Int, val road: String)

enum class TurnKind(val label: String) {
  STRAIGHT("직진"),
  RIGHT("우회전"),
  LEFT("좌회전"),
  KEEP_RIGHT("오른쪽 방향"),
  KEEP_LEFT("왼쪽 방향"),
  U_TURN("유턴"),
  OTHER("안내"),
}
