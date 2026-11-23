package com.kscho.way.presentation.home

import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.pickup.PickupDraft
import com.kscho.way.domain.usecase.history.GetRecentPickupsUseCase
import com.kscho.way.domain.usecase.vehicle.GetCarLocationUseCase
import com.kscho.way.presentation.component.base.BaseViewModel
import com.kscho.way.presentation.component.base.UiEvent
import com.kscho.way.presentation.component.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class HomeUiState(
  val recent: List<PickupRecord> = emptyList(),
  val here: LatLng? = null,
) : UiState

sealed interface HomeEvent : UiEvent {
  data object NavigateToChoosePassenger : HomeEvent

  data object NavigateToAdjustPin : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
  private val getRecentPickups: GetRecentPickupsUseCase,
  private val getCarLocation: GetCarLocationUseCase,
  private val draft: PickupDraft,
) : BaseViewModel<HomeUiState, HomeEvent>(HomeUiState()) {
  fun refresh() {
    viewModelScope.launch {
      runCatching { getRecentPickups(limit = RECENT_LIMIT) }.onSuccess { recent -> updateState { copy(recent = recent) } }
      val here = runCatching { getCarLocation() }.getOrNull() ?: return@launch
      updateState { copy(here = here) }
    }
  }

  fun createPickup() {
    draft.reset()
    emitEvent(HomeEvent.NavigateToChoosePassenger)
  }

  fun reuse(record: PickupRecord) {
    draft.reuse(record)
    emitEvent(HomeEvent.NavigateToAdjustPin)
  }

  private companion object {
    const val RECENT_LIMIT = 3
  }
}
