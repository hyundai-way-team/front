package com.kscho.way.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kscho.way.WayApp
import com.kscho.way.data.mock.MockWorld
import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.PickupPlan
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LatLng
import com.kscho.way.pickup.PickupService
import kotlinx.coroutines.launch

/**
 * 디버그 빌드 전용 — 화면을 거치지 않고 mock 픽업을 움직인다. 릴리스엔 들어가지 않는다.
 * (에뮬 현재 사용자는 10 → --user 10)
 *
 *   시작   adb shell am broadcast --user 10 -a com.kscho.way.debug.START_MOCK_PICKUP -p com.kscho.way
 *          (이서연 · 신촌 XX 빌라 골목 입구 앞 — 화면에서 출발한 것과 같다)
 *   정차   adb shell am broadcast --user 10 -a com.kscho.way.debug.ARRIVE -p com.kscho.way
 *   탑승   adb shell am broadcast --user 10 -a com.kscho.way.debug.BOARD -p com.kscho.way
 *   끝     adb shell am broadcast --user 10 -a com.kscho.way.debug.CLEAR_PICKUP -p com.kscho.way
 *   시연 주행 경로(픽업까지 이 길을 빨리 달림 — DemoDriveNavigator)
 *          adb shell am broadcast --user 10 -a com.kscho.way.debug.DEMO_ROUTE -p com.kscho.way --es path "위도,경도;위도,경도;…" --ei speedup 6 --ef speed 8
 *   시연 주행 끄기 adb shell am broadcast --user 10 -a com.kscho.way.debug.DEMO_CLEAR -p com.kscho.way
 *
 * 포그라운드 서비스는 앱이 앞에 있을 때만 시작된다. 뒤에서 막히면 Way 를 한 번 띄우고 다시 시작한다.
 */
class DebugPickupReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val c = (context.applicationContext as WayApp).container
    when (intent.action) {
      START -> {
        val pending = goAsync()
        c.scope.launch {
          try {
            // 연락처·장소가 실제 데이터라 시연용 이름이 없을 수 있다 — 첫 연락처(없으면 시험용 사람)와 신촌 골목 입구 좌표로
            val passenger = c.contacts.search("").firstOrNull() ?: Contact("debug-1", "이서연", "01000001234")
            val place: Place? = null
            val check = c.checkPickupPoint(MockWorld.VILLA_ALLEY_ENTRANCE, place)
            val vehicle = c.vehicleProfile.myVehicle()
            if (c.coordinator.hasActivePickup) c.coordinator.finish()
            c.coordinator.start(PickupPlan(passenger, place, check.point, check.restriction, check.route, vehicle))
            val service = PickupService.start(context)
            Log.i(TAG, "mock 픽업 시작 (픽업 서비스 ${if (service) "시작" else "막힘"})")
          } catch (e: Exception) {
            Log.w(TAG, "mock 픽업 시작 실패", e)
          } finally {
            pending.finish()
          }
        }
      }
      ARRIVE -> {
        c.coordinator.arriveNow()
        Log.i(TAG, "픽업 위치 정차")
      }
      BOARD -> {
        c.coordinator.confirmBoarded(BoardingSource.MANUAL)
        Log.i(TAG, "탑승")
      }
      CLEAR -> {
        c.coordinator.finish()
        Log.i(TAG, "픽업 지움")
      }
      DEMO_ROUTE -> {
        val demo = c.navigator as? DemoDriveNavigator
        if (demo == null) {
          Log.w(TAG, "시연 주행을 끼울 수 없는 내비")
          return
        }
        val path =
          intent.getStringExtra("path").orEmpty().split(';').mapNotNull { p ->
            val (lat, lng) = p.split(',').map { it.trim().toDoubleOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            if (lat == null || lng == null) null else LatLng(lat, lng)
          }
        if (path.size < 2) {
          Log.w(TAG, "시연 경로가 짧아요(${path.size}점)")
          return
        }
        demo.setRoute(path, intent.getIntExtra("speedup", 6), intent.getFloatExtra("speed", 8f).toDouble())
        Log.i(TAG, "시연 주행 경로 ${path.size}점 — 출발 지점 ${path.first()}")
      }
      DEMO_CLEAR -> {
        (c.navigator as? DemoDriveNavigator)?.clear()
        Log.i(TAG, "시연 주행 끔")
      }
    }
  }

  private companion object {
    const val TAG = "WayDebug"
    const val START = "com.kscho.way.debug.START_MOCK_PICKUP"
    const val ARRIVE = "com.kscho.way.debug.ARRIVE"
    const val BOARD = "com.kscho.way.debug.BOARD"
    const val CLEAR = "com.kscho.way.debug.CLEAR_PICKUP"
    const val DEMO_ROUTE = "com.kscho.way.debug.DEMO_ROUTE"
    const val DEMO_CLEAR = "com.kscho.way.debug.DEMO_CLEAR"
  }
}
