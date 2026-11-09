package com.kscho.way.di

import android.content.Context
import android.util.Log
import com.kscho.way.data.mock.MockPleosContacts
import com.kscho.way.data.mock.MockWayApi
import com.kscho.way.data.mock.SimulatedNavigator
import com.kscho.way.data.remote.WayApi
import com.kscho.way.data.repository.ContactRepositoryImpl
import com.kscho.way.data.repository.PickupHistoryRepositoryImpl
import com.kscho.way.data.repository.PickupRepositoryImpl
import com.kscho.way.data.repository.PlaceRepositoryImpl
import com.kscho.way.data.repository.StopRestrictionRepositoryImpl
import com.kscho.way.data.repository.VehicleProfileRepositoryImpl
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.repository.ContactRepository
import com.kscho.way.domain.repository.PickupHistoryRepository
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.repository.VehicleProfileRepository
import com.kscho.way.domain.usecase.CheckPickupPoint
import com.kscho.way.domain.vehicle.BoardingSensor
import com.kscho.way.domain.vehicle.Navigator
import com.kscho.way.ui.plan.PickupDraft
import com.kscho.way.vehicle.PleosBoardingSensor
import com.kscho.way.vehicle.PleosNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 손으로 하는 의존성 주입. 서버가 생기면 [api] 만 HTTP 구현으로 바꾼다.
 */
class AppContainer(context: Context) {
  private val app = context.applicationContext
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val clock: () -> Long = System::currentTimeMillis

  /** mock 서버 — 보낸 문자는 logcat WayMock 에 찍힌다. */
  val mockApi = MockWayApi(scope, clock, log = { Log.i("WayMock", it) })
  private val api: WayApi = mockApi

  val contacts: ContactRepository = ContactRepositoryImpl(MockPleosContacts())
  val places: PlaceRepository = PlaceRepositoryImpl(api)
  val vehicleProfile: VehicleProfileRepository = VehicleProfileRepositoryImpl(api)
  val history: PickupHistoryRepository = PickupHistoryRepositoryImpl(api)
  private val restrictions = StopRestrictionRepositoryImpl(api)
  private val pickups = PickupRepositoryImpl(api)

  val navigator: Navigator =
    if (WayConfig.SIMULATED_DRIVE) SimulatedNavigator(scope, clock) else PleosNavigator(app, clock)

  val boardingSensor: BoardingSensor = PleosBoardingSensor(app, clock).also { it.start() }

  val checkPickupPoint = CheckPickupPoint(places, restrictions, navigator, clock)

  val coordinator =
    PickupCoordinator(pickups, navigator, boardingSensor, scope, clock, onError = { what, e -> Log.w("WayPickup", "$what 실패", e) })

  /** 출발 전 화면들(D-02~D-05)이 함께 채우는 픽업 계획. */
  val draft = PickupDraft()
}
