package com.kscho.way.presentation.drive

import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.domain.usecase.restriction.GetPickupZonesUseCase
import com.kscho.way.presentation.component.base.BaseViewModel
import com.kscho.way.presentation.component.base.UiEvent
import com.kscho.way.presentation.component.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class DriveUiState(
  val pickup: PickupState? = null,
  val doorAvailable: Boolean? = null,
  val zones: List<RestrictedZone> = emptyList(),
) : UiState

sealed interface DriveEvent : UiEvent {
  data object Closed : DriveEvent
}

@HiltViewModel
class DriveViewModel @Inject constructor(
  private val coordinator: PickupCoordinator,
  boardingSensor: BoardingSensor,
  private val getPickupZones: GetPickupZonesUseCase,
) : BaseViewModel<DriveUiState, DriveEvent>(DriveUiState(pickup = coordinator.state.value)) {
  private var closed = false

  init {
    coordinator.state.onEach { pickup -> updateState { copy(pickup = pickup) } }.launchIn(viewModelScope)
    boardingSensor.available.onEach { available -> updateState { copy(doorAvailable = available) } }.launchIn(viewModelScope)
    coordinator.state.value?.plan?.pickupPoint?.location?.let { pickup ->
      viewModelScope.launch {
        val zones = runCatching { getPickupZones(pickup) }.getOrDefault(emptyList())
        updateState { copy(zones = zones) }
      }
    }
  }

  fun confirmBoarded() {
    coordinator.confirmBoarded(BoardingSource.MANUAL)
  }

  fun finish() {
    close { coordinator.finish() }
  }

  fun connectNext() {
    close { coordinator.connectNext() }
  }

  fun cancelNext() {
    coordinator.cancelNext()
  }

  private fun close(action: () -> Unit) {
    if (closed) return
    closed = true
    action()
    emitEvent(DriveEvent.Closed)
  }
}
