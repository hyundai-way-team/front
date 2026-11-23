package com.kscho.way.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kscho.way.data.fake.FakeWorld
import com.kscho.way.di.ApplicationScope
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.usecase.contact.SearchContactsUseCase
import com.kscho.way.domain.usecase.pickup.CheckPickupPointUseCase
import com.kscho.way.domain.usecase.vehicle.GetMyVehicleUseCase
import com.kscho.way.pickup.PickupService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DebugPickupReceiver : BroadcastReceiver() {
  @Inject lateinit var coordinator: PickupCoordinator

  @Inject lateinit var searchContacts: SearchContactsUseCase

  @Inject lateinit var checkPickupPoint: CheckPickupPointUseCase

  @Inject lateinit var getMyVehicle: GetMyVehicleUseCase

  @Inject lateinit var navigator: Navigator

  @Inject @field:ApplicationScope lateinit var scope: CoroutineScope

  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      START -> {
        val pending = goAsync()
        scope.launch {
          try {
            val passenger = searchContacts("").firstOrNull() ?: Contact("debug-1", "이서연", "01000001234")
            val place: Place? = null
            val check = checkPickupPoint(FakeWorld.VILLA_ALLEY_ENTRANCE, place)
            val vehicle = getMyVehicle()
            if (coordinator.hasActivePickup) coordinator.finish()
            coordinator.start(PickupPlan(passenger, place, check.point, check.restriction, check.route, vehicle))
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
        coordinator.arriveNow()
        Log.i(TAG, "픽업 위치 정차")
      }
      BOARD -> {
        coordinator.confirmBoarded(BoardingSource.MANUAL)
        Log.i(TAG, "탑승")
      }
      CLEAR -> {
        coordinator.finish()
        Log.i(TAG, "픽업 지움")
      }
      DEMO_ROUTE -> {
        val demo = navigator as? DemoDriveNavigator
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
        (navigator as? DemoDriveNavigator)?.clear()
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
