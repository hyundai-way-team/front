package com.kscho.way.domain.model

/** 출발 전 경로 미리보기. [path] 는 지도에 그릴 선(모르면 비어 있음). */
data class RoutePreview(val distanceMeters: Int, val durationSeconds: Int, val path: List<LatLng> = emptyList())

/** 내비 안내 중 들어오는 일. */
sealed interface NavEvent {
  /** 픽업 위치까지 남은 시간·거리. [arrivalAtMillis] = 도착 예정 시각(epoch ms). */
  data class Progress(val location: LatLng?, val remainingSeconds: Int, val remainingMeters: Int, val arrivalAtMillis: Long) : NavEvent

  /** 픽업 위치 도착·정차. */
  data class Arrived(val atMillis: Long) : NavEvent

  /** 다음 회전 안내(Pleos 내비 TBT) — 운전 화면 오른쪽 위 '300m 앞 우회전'. */
  data class Turn(val instruction: TurnInstruction) : NavEvent
}

/** 다음 회전 안내 한 건. [road] 는 내비가 주는 설명(대개 도로·방면 이름, 없으면 빈 글자). */
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
