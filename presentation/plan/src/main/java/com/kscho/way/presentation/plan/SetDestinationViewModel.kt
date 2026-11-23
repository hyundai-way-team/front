package com.kscho.way.presentation.plan

import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.pickup.PickupDraft
import com.kscho.way.domain.usecase.place.SearchPlacesUseCase
import com.kscho.way.presentation.component.base.BaseViewModel
import com.kscho.way.presentation.component.base.UiEvent
import com.kscho.way.presentation.component.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

data class SetDestinationUiState(
  val pickup: LatLng? = null,
  val query: String = "",
  val results: List<Place> = emptyList(),
  val selected: Place? = null,
) : UiState

sealed interface SetDestinationEvent : UiEvent {
  data object NavigateToConfirmDeparture : SetDestinationEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SetDestinationViewModel @Inject constructor(
  private val searchPlaces: SearchPlacesUseCase,
  private val draft: PickupDraft,
) : BaseViewModel<SetDestinationUiState, SetDestinationEvent>(
  SetDestinationUiState(pickup = draft.check.value?.point?.location, query = draft.destination.value?.name.orEmpty()),
) {
  private val query = MutableStateFlow(currentState.query)

  init {
    query
      .debounce(SEARCH_DEBOUNCE_MILLIS)
      .mapLatest { runCatching { searchPlaces(it) }.getOrDefault(emptyList()) }
      .onEach { results -> updateState { copy(results = results) } }
      .launchIn(viewModelScope)
    draft.destination.onEach { selected -> updateState { copy(selected = selected) } }.launchIn(viewModelScope)
  }

  fun changeQuery(value: String) {
    query.value = value
    updateState { copy(query = value) }
    if (value.isBlank()) draft.destination.value = null
  }

  fun select(place: Place) {
    draft.destination.value = place
  }

  fun next() {
    emitEvent(SetDestinationEvent.NavigateToConfirmDeparture)
  }

  private companion object {
    const val SEARCH_DEBOUNCE_MILLIS = 250L
  }
}
