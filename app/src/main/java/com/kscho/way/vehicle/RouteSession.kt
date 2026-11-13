package com.kscho.way.vehicle

/**
 * Pleos 내비에 건 안내 한 번(요청 → 경로 시작 → 도착).
 *
 * 새 경로를 요청한 직후 내비는 지난 경로의 소식(예: 탑승 뒤 넘긴 목적지의 '남은 0초·0m')을 한 번 더 보낸다.
 * 그걸 받으면 출발하자마자 '곧 도착'·'도착 시간 변경' 문자가 나가므로, 요청한 경로가 시작(onRouteStarted)되기 전 소식은 버린다.
 */
internal class RouteSession {
  @Volatile private var guiding = false
  @Volatile private var started = false
  @Volatile private var arrived = false

  /** 진행·회전·도착 소식을 받을 때인지. */
  val live: Boolean
    get() = guiding && started && !arrived

  fun requested() {
    guiding = true
    started = false
    arrived = false
  }

  fun started() {
    if (guiding) started = true
  }

  /** 도착 소식을 받을지 — 받으면 그 뒤 소식은 버린다(한 번만 true). */
  @Synchronized
  fun arrive(): Boolean {
    if (!live) return false
    arrived = true
    return true
  }

  fun stopped() {
    guiding = false
    started = false
  }

  /** 아직 도착 전인 안내가 걸려 있는지(취소할 게 있는지). */
  val guidingUnarrived: Boolean
    get() = guiding && !arrived
}
