package com.kscho.way.di

import android.content.Context
import android.util.Log
import com.kscho.way.data.mock.MockPleosContacts
import com.kscho.way.data.mock.MockWayApi
import com.kscho.way.data.mock.SimulatedNavigator
import com.kscho.way.data.remote.HttpWayApi
import com.kscho.way.data.remote.WayApi
import com.kscho.way.data.repository.ContactRepositoryImpl
import com.kscho.way.data.repository.PickupHistoryRepositoryImpl
import com.kscho.way.data.repository.PickupRepositoryImpl
import com.kscho.way.data.repository.PlaceRepositoryImpl
import com.kscho.way.data.repository.StopRestrictionRepositoryImpl
import com.kscho.way.data.repository.VehicleProfileRepositoryImpl
import com.kscho.way.domain.messaging.PassengerMessenger
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.repository.ContactRepository
import com.kscho.way.domain.repository.PickupHistoryRepository
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.repository.VehicleProfileRepository
import com.kscho.way.domain.usecase.CheckPickupPoint
import com.kscho.way.domain.vehicle.BoardingSensor
import com.kscho.way.domain.vehicle.Navigator
import com.kscho.way.pickup.PleosMessenger
import com.kscho.way.ui.plan.PickupDraft
import com.kscho.way.vehicle.PleosBoardingSensor
import com.kscho.way.vehicle.PleosNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 손으로 하는 의존성 주입. 서버 연동은 [api] 하나만 바꾼다(HTTP · mock).
 */
class AppContainer(context: Context) {
  private val app = context.applicationContext
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val clock: () -> Long = System::currentTimeMillis

  /**
   * 서버 API. [WayConfig.SERVER_URL] 이 있으면 그 서버(로컬 서버 · 실제 서버)에 붙고,
   * 없으면 앱 안 mock 서버 — 보낸 문자는 logcat WayMock 에 찍힌다.
   */
  private val api: WayApi =
    WayConfig.SERVER_URL?.let { HttpWayApi(it) } ?: MockWayApi(scope, clock, log = { Log.i("WayMock", it) })

  val contacts: ContactRepository = ContactRepositoryImpl(MockPleosContacts())
  val places: PlaceRepository = PlaceRepositoryImpl(api)
  val vehicleProfile: VehicleProfileRepository = VehicleProfileRepositoryImpl(api)
  val history: PickupHistoryRepository = PickupHistoryRepositoryImpl(api)
  private val restrictions = StopRestrictionRepositoryImpl(api)
  private val pickups = PickupRepositoryImpl(api)

  val navigator: Navigator =
    if (WayConfig.SIMULATED_DRIVE) SimulatedNavigator(scope, clock) else PleosNavigator(app, clock)

  val boardingSensor: BoardingSensor = PleosBoardingSensor(app, clock).also { it.start() }

  /** 동승자 문자 — 차에서 운전자 번호로(Pleos 문자). */
  private val messenger: PassengerMessenger = PleosMessenger(app)

  val checkPickupPoint = CheckPickupPoint(places, restrictions, navigator, clock)

  val coordinator =
    PickupCoordinator(pickups, navigator, boardingSensor, messenger, scope, clock, onError = { what, e -> Log.w("WayPickup", "$what 실패", e) })

  /** 출발 전 화면들(D-02~D-05)이 함께 채우는 픽업 계획. */
  val draft = PickupDraft()
}
