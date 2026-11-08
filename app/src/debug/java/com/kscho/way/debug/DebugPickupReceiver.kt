package com.kscho.way.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kscho.way.pickup.PickupSession
import com.kscho.way.pickup.PickupSessionStore

/**
 * 디버그 빌드 전용 — 서버 없이 mock 픽업을 시작·종료해 Gleo 응답·화면을 시험한다. 릴리스엔 들어가지 않는다.
 *
 *   adb shell am broadcast -a com.kscho.way.debug.START_MOCK_PICKUP -p com.kscho.way
 *   adb shell am broadcast -a com.kscho.way.debug.CLEAR_PICKUP -p com.kscho.way
 */
class DebugPickupReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      START -> {
        // MVP 페르소나: 운전자 김도윤이 동승자 이서연을 태우러 간다 (번호는 가짜)
        PickupSessionStore.start(
          PickupSession(
            passengerName = "이서연",
            passengerPhone = "01000000000",
            pickupPlaceName = "역삼역 3번 출구",
            remainingSeconds = 410,
            passengerCheckedLink = true,
            passengerSharingLocation = true,
          )
        )
        Log.i(TAG, "mock 픽업 시작")
      }
      CLEAR -> {
        PickupSessionStore.clear()
        Log.i(TAG, "픽업 지움")
      }
    }
  }

  private companion object {
    const val TAG = "WayDebug"
    const val START = "com.kscho.way.debug.START_MOCK_PICKUP"
    const val CLEAR = "com.kscho.way.debug.CLEAR_PICKUP"
  }
}
