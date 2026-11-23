package com.kscho.way.domain.pickup

class DoorBoardingDetector {
  private val open = mutableSetOf<String>()

  fun onDoor(door: String, isOpen: Boolean): Boolean =
    if (isOpen) {
      open += door
      false
    } else {
      open.remove(door)
    }
}
