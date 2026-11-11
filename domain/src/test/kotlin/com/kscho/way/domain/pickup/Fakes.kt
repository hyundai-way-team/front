package com.kscho.way.domain.pickup

import com.kscho.way.domain.messaging.PassengerMessenger
import com.kscho.way.domain.messaging.PassengerText
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.NavEvent
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.PickupOutcome
import com.kscho.way.domain.model.PickupPlan
import com.kscho.way.domain.model.PickupRecord
import com.kscho.way.domain.model.RoutePreview
import com.kscho.way.domain.repository.CreatedPickup
import com.kscho.way.domain.repository.PickupHistoryRepository
import com.kscho.way.domain.repository.PickupRepository
import com.kscho.way.domain.vehicle.BoardingSensor
import com.kscho.way.domain.vehicle.Navigator
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
  var failCreate: Exception? = null

  override suspend fun create(plan: PickupPlan, arrivalAtMillis: Long): CreatedPickup {
    failCreate?.let { throw it }
    created += plan to arrivalAtMillis
    return CreatedPickup("p${created.size}", "https://way.mock/p/${created.size}")
  }

  override fun passengerStatus(pickupId: String): Flow<PassengerStatus> = passenger

  override suspend fun reportDriver(pickupId: String, location: LatLng?, remainingSeconds: Int, remainingMeters: Int, arrivalAtMillis: Long) {
    driverReports += remainingSeconds
  }

  override suspend fun notifyEtaChanged(pickupId: String, previousArrivalAtMillis: Long, arrivalAtMillis: Long) {
    etaChanges += previousArrivalAtMillis to arrivalAtMillis
  }

  override suspend fun notifyArrivingSoon(pickupId: String, arrivalAtMillis: Long) {
    arrivingSoon += arrivalAtMillis
  }

  override suspend fun reportArrived(pickupId: String, arrivedAtMillis: Long, waitSeconds: Int) {
    arrivals += arrivedAtMillis
  }

  override suspend fun complete(pickupId: String, outcome: PickupOutcome) {
    completed += outcome
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
