package com.kscho.way.presentation.plan

import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.gateway.Clock
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.model.pickup.PickupPointCheck
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.vehicle.MyVehicle
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.pickup.PickupDraft
import com.kscho.way.domain.usecase.vehicle.GetMyVehicleUseCase
import com.kscho.way.presentation.component.base.BaseViewModel
import com.kscho.way.presentation.component.base.UiEvent
import com.kscho.way.presentation.component.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ConfirmDepartureUiState(
  val passenger: Contact? = null,
  val check: PickupPointCheck? = null,
  val arrivalAtMillis: Long = 0L,
  val destination: Place? = null,
  val vehicle: MyVehicle? = null,
  val starting: Boolean = false,
  val error: String? = null,
) : UiState

sealed interface ConfirmDepartureEvent : UiEvent {
  data object NavigateBack : ConfirmDepartureEvent

  data object PickupStarted : ConfirmDepartureEvent
}

@HiltViewModel
class ConfirmDepartureViewModel @Inject constructor(
  private val getMyVehicle: GetMyVehicleUseCase,
  private val coordinator: PickupCoordinator,
  private val draft: PickupDraft,
  clock: Clock,
) : BaseViewModel<ConfirmDepartureUiState, ConfirmDepartureEvent>(
  ConfirmDepartureUiState(
    passenger = draft.passenger.value,
    check = draft.check.value,
    arrivalAtMillis = clock.now() + (draft.check.value?.route?.durationSeconds ?: 0) * 1000L,
  ),
) {
  private val place = draft.place.value

  init {
    if (currentState.passenger == null || currentState.check == null) emitEvent(ConfirmDepartureEvent.NavigateBack)
    draft.destination.onEach { destination -> updateState { copy(destination = destination) } }.launchIn(viewModelScope)
    viewModelScope.launch {
      runCatching { getMyVehicle() }
        .onSuccess { vehicle -> updateState { copy(vehicle = vehicle) } }
        .onFailure { updateState { copy(error = VEHICLE_FAILED_MESSAGE) } }
    }
  }

  fun clearDestination() {
    draft.destination.value = null
  }

  fun start() {
    val state = currentState
    val passenger = state.passenger ?: return
    val check = state.check ?: return
    val vehicle = state.vehicle ?: return
    if (state.starting) return
    updateState { copy(starting = true, error = null) }
    viewModelScope.launch {
      try {
        coordinator.start(PickupPlan(passenger, place, check.point, check.restriction, check.route, vehicle, state.destination))
        draft.reset()
        emitEvent(ConfirmDepartureEvent.PickupStarted)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        updateState { copy(starting = false, error = e.message ?: START_FAILED_MESSAGE) }
      }
    }
  }

  private companion object {
    const val VEHICLE_FAILED_MESSAGE = "내 차 정보를 불러오지 못했어요"
    const val START_FAILED_MESSAGE = "출발하지 못했어요"
  }
}
