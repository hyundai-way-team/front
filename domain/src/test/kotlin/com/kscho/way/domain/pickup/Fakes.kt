package com.kscho.way.domain.pickup

import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.gateway.PassengerMessenger
import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.base.FailState
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.message.PassengerText
import com.kscho.way.domain.model.passenger.PassengerStatus
import com.kscho.way.domain.model.pickup.CreatedPickup
import com.kscho.way.domain.model.pickup.PickupOutcome
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.model.route.NavEvent
import com.kscho.way.domain.model.route.RoutePreview
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import com.kscho.way.domain.repository.pickup.PickupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNavigator(var route: RoutePreview = RoutePreview(4_000, 12 * 60)) : Navigator {
  override val events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 16)
  var guidedTo: LatLng? = null
  var stopped = 0
  var failGuidance: Exception? = null
  val previewed = mutableListOf<LatLng>()

  override suspend fun currentLocation(): LatLng? = null

  override suspend fun preview(destination: LatLng, name: String): RoutePreview {
    previewed += destination
    return route
  }

  override suspend fun startGuidance(destination: LatLng, name: String, address: String) {
    failGuidance?.let { throw it }
    guidedTo = destination
  }

  override fun stopGuidance() {
    stopped++
  }

  var handedOverTo: LatLng? = null
  var handOvers = 0

  override suspend fun handOver(destination: LatLng, name: String, address: String) {
    handedOverTo = destination
    handOvers++
  }

  var naviOpened = 0

  override fun openNavi() {
    naviOpened++
  }
}

class FakeBoardingSensor : BoardingSensor {
  override val available = MutableStateFlow<Boolean?>(true)
  override val boardings = MutableSharedFlow<Long>(extraBufferCapacity = 4)
}

class FakePickupRepository : PickupRepository {
  val created = mutableListOf<Pair<PickupPlan, Long>>()
  val passenger = MutableStateFlow(PassengerStatus())
  val arrivingSoon = mutableListOf<Long>()
  val etaChanges = mutableListOf<Pair<Long, Long>>()
  val arrivals = mutableListOf<Long>()
  val completed = mutableListOf<PickupOutcome>()
  val driverReports = mutableListOf<Int>()
  val driverLocations = mutableListOf<LatLng?>()
  var failCreate: FailState? = null
  var failReports: FailState? = null

  override suspend fun create(plan: PickupPlan, arrivalAtMillis: Long): ApiState<CreatedPickup> {
    failCreate?.let { return ApiState.Fail(it) }
    created += plan to arrivalAtMillis
    return ApiState.Success(CreatedPickup("p${created.size}", "https://way.mock/p/${created.size}"))
  }

  private fun reported(): ApiState<Unit> = failReports?.let { ApiState.Fail(it) } ?: ApiState.Success(Unit)

  override fun passengerStatus(pickupId: String): Flow<PassengerStatus> = passenger

  override suspend fun reportDriver(pickupId: String, location: LatLng?, remainingSeconds: Int, remainingMeters: Int, arrivalAtMillis: Long): ApiState<Unit> {
    driverReports += remainingSeconds
    driverLocations += location
    return reported()
  }

  override suspend fun notifyEtaChanged(pickupId: String, previousArrivalAtMillis: Long, arrivalAtMillis: Long): ApiState<Unit> {
    etaChanges += previousArrivalAtMillis to arrivalAtMillis
    return reported()
  }

  override suspend fun notifyArrivingSoon(pickupId: String, arrivalAtMillis: Long): ApiState<Unit> {
    arrivingSoon += arrivalAtMillis
    return reported()
  }

  override suspend fun reportArrived(pickupId: String, arrivedAtMillis: Long, waitSeconds: Int): ApiState<Unit> {
    arrivals += arrivedAtMillis
    return reported()
  }

  override suspend fun complete(pickupId: String, outcome: PickupOutcome): ApiState<Unit> {
    completed += outcome
    return reported()
  }
}

class FakePickupHistory : PickupHistoryRepository {
  val saved = mutableListOf<PickupRecord>()

  override suspend fun recent(limit: Int): List<PickupRecord> = saved.reversed().take(limit)

  override suspend fun save(record: PickupRecord) {
    saved += record
  }
}

class FakePassengerMessenger : PassengerMessenger {
  val sent = mutableListOf<Pair<Contact, PassengerText>>()

  override suspend fun send(to: Contact, text: PassengerText) {
    sent += to to text
  }
}
