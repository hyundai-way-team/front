package com.kscho.way.domain.vehicle

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 도어 열림·닫힘으로 탑승 감지(기획 3-10). */
interface BoardingSensor {
  /** 도어 이벤트를 쓸 수 있는지. 아직 모르면 null, false 면 수동 '탑승 완료' 버튼을 보인다. */
  val available: StateFlow<Boolean?>

  /** 동승자 쪽 도어가 열렸다 닫힌 시각(epoch ms). */
  val boardings: SharedFlow<Long>
}

/** 도어 위치 → 탑승. 열렸던 문이 닫히면 탑승으로 본다. */
class DoorBoardingDetector {
  private val open = mutableSetOf<String>()

  /** 도어 위치가 바뀔 때마다 부른다. 이번 변화가 탑승(열림→닫힘)이면 true. */
  fun onDoor(door: String, isOpen: Boolean): Boolean =
    if (isOpen) {
      open += door
      false
    } else {
      open.remove(door)
    }
}
