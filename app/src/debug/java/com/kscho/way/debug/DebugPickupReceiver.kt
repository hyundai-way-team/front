package com.kscho.way.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.kscho.way.pickup.PickupService
import com.kscho.way.pickup.PickupSession
import com.kscho.way.pickup.PickupSessionStore

/**
 * 디버그 빌드 전용 — 서버·내비 없이 mock 픽업을 움직여 Gleo 응답·음성 질문을 시험한다. 릴리스엔 들어가지 않는다.
 * (에뮬 현재 사용자는 10 → --user 10)
 *
 *   시작   adb shell am broadcast --user 10 -a com.kscho.way.debug.START_MOCK_PICKUP -p com.kscho.way
 *   정차   adb shell am broadcast --user 10 -a com.kscho.way.debug.ARRIVE -p com.kscho.way [--el ago_ms 40000]
 *          (ago_ms 만큼 전에 정차한 것으로 — 40000 이면 5초 뒤 전화 연결 제안)
 *   탑승   adb shell am broadcast --user 10 -a com.kscho.way.debug.BOARD -p com.kscho.way
 *   끝     adb shell am broadcast --user 10 -a com.kscho.way.debug.CLEAR_PICKUP -p com.kscho.way
 *
 * 포그라운드 서비스는 앱이 앞에 있을 때만 시작된다. 뒤에서 막히면 Way 를 한 번 띄우고 다시 시작한다.
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
        val service = PickupService.start(context)
        Log.i(TAG, "mock 픽업 시작 (픽업 서비스 ${if (service) "시작" else "막힘"})")
      }
      ARRIVE -> {
        val ago = intent.getLongExtra("ago_ms", 0L)
        PickupSessionStore.markArrived(SystemClock.elapsedRealtime() - ago)
        Log.i(TAG, "픽업 위치 정차 (${ago}ms 전)")
      }
      BOARD -> {
        PickupSessionStore.markBoarded()
        Log.i(TAG, "탑승")
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
    const val ARRIVE = "com.kscho.way.debug.ARRIVE"
    const val BOARD = "com.kscho.way.debug.BOARD"
    const val CLEAR = "com.kscho.way.debug.CLEAR_PICKUP"
  }
}
