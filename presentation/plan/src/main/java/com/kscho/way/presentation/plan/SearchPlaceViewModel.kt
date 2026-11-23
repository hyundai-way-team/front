package com.kscho.way.presentation.plan

import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.pickup.PickupDraft
import com.kscho.way.domain.usecase.place.SearchPlacesUseCase
import com.kscho.way.domain.usecase.restriction.GetPickupZonesUseCase
import com.kscho.way.domain.usecase.vehicle.GetCarLocationUseCase
import com.kscho.way.presentation.component.base.BaseViewModel
import com.kscho.way.presentation.component.base.UiEvent
import com.kscho.way.presentation.component.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SearchPlaceUiState(
  val passengerName: String = "",
  val query: String = "",
  val results: List<Place> = emptyList(),
  val selected: Place? = null,
  val here: LatLng? = null,
  val zones: List<RestrictedZone> = emptyList(),
) : UiState

sealed interface SearchPlaceEvent : UiEvent {
  data object NavigateToAdjustPin : SearchPlaceEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchPlaceViewModel @Inject constructor(
  private val searchPlaces: SearchPlacesUseCase,
  private val getPickupZones: GetPickupZonesUseCase,
  private val getCarLocation: GetCarLocationUseCase,
  private val draft: PickupDraft,
) : BaseViewModel<SearchPlaceUiState, SearchPlaceEvent>(
  SearchPlaceUiState(passengerName = draft.passenger.value?.callName.orEmpty(), query = draft.place.value?.name.orEmpty()),
) {
  private val query = MutableStateFlow(currentState.query)
  private var zonesJob: Job? = null

  init {
    query
      .debounce(SEARCH_DEBOUNCE_MILLIS)
      .mapLatest { runCatching { searchPlaces(it) }.getOrDefault(emptyList()) }
      .onEach { results -> updateState { copy(results = results) } }
      .launchIn(viewModelScope)
    draft.place.onEach { selected -> updateState { copy(selected = selected) } }.launchIn(viewModelScope)
  }

  fun load() {
    viewModelScope.launch {
      val place = draft.place.value
      val here = place?.location ?: runCatching { getCarLocation() }.getOrNull() ?: return@launch
      updateState { copy(here = here) }
      if (place != null) loadZones(place.location)
    }
  }

  fun changeQuery(value: String) {
    query.value = value
    updateState { copy(query = value) }
  }

  fun select(place: Place) {
    draft.place.value = place
    draft.pinStart.value = place.location
    draft.check.value = null
    updateState { copy(zones = emptyList()) }
    zonesJob?.cancel()
    zonesJob = viewModelScope.launch { loadZones(place.location) }
  }

  fun next() {
    emitEvent(SearchPlaceEvent.NavigateToAdjustPin)
  }

  private suspend fun loadZones(at: LatLng) {
    val zones = runCatching { getPickupZones(at) }.getOrDefault(emptyList())
    updateState { copy(zones = zones) }
  }

  private companion object {
    const val SEARCH_DEBOUNCE_MILLIS = 250L
  }
}
