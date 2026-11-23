package com.kscho.way.data.device.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import android.content.Context
import android.util.Log
import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.pickup.DoorBoardingDetector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class PleosBoardingSensor(context: Context, private val clock: () -> Long) : BoardingSensor {
  private val app = context.applicationContext
  private val _available = MutableStateFlow<Boolean?>(null)
  override val available: StateFlow<Boolean?> = _available.asStateFlow()
  private val _boardings = MutableSharedFlow<Long>(extraBufferCapacity = 8)
  override val boardings: SharedFlow<Long> = _boardings.asSharedFlow()
  private val detector = DoorBoardingDetector()

  private val passengerDoors = listOf(DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)

  fun start() {
    try {
      val vehicle = Vehicle(app)
      vehicle.initialize()
      val door = vehicle.getDoor()
      door.checkDoorPositionCapabilities(
        { caps -> _available.value = caps.isNotEmpty() },
        { e ->
          Log.w(TAG, "도어 위치를 읽을 수 없음 — 수동 탑승 버튼", e)
          _available.value = false
        },
      )
      for (value in passengerDoors) {
        door.registerDoorPosition(
          DoorArea(value),
          object : DoorPositionListener {
            override fun onPositionUpdated(position: Int?) = onDoor(value, (position ?: 0) > 0)

            override fun onFailed(exception: Exception) {
              Log.w(TAG, "도어 $value 구독 실패", exception)
            }
          },
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "Vehicle SDK 를 쓸 수 없음 — 수동 탑승 버튼", e)
      _available.value = false
    }
  }

  private fun onDoor(value: DoorValue, open: Boolean) {
    val boarded = synchronized(detector) { detector.onDoor(value.name, open) }
    Log.i(TAG, "도어 $value ${if (open) "열림" else "닫힘"}${if (boarded) " → 탑승" else ""}")
    if (boarded) _boardings.tryEmit(clock())
  }

  private companion object {
    const val TAG = "WayDoor"
  }
}
