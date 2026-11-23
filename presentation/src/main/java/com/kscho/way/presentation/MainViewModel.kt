package com.kscho.way.presentation

import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.presentation.component.base.BaseViewModel
import com.kscho.way.presentation.component.base.UiEvent
import com.kscho.way.presentation.component.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class MainUiState(val activePickupId: String? = null) : UiState

sealed interface MainEvent : UiEvent

@HiltViewModel
class MainViewModel @Inject constructor(
  coordinator: PickupCoordinator,
) : BaseViewModel<MainUiState, MainEvent>(MainUiState(activePickupId = coordinator.state.value?.id)) {
  init {
    coordinator.state.onEach { pickup -> updateState { copy(activePickupId = pickup?.id) } }.launchIn(viewModelScope)
  }
}
