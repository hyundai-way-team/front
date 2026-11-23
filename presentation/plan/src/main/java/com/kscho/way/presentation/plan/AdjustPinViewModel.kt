package com.kscho.way.presentation.plan

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.geo.MapArea
import com.kscho.way.domain.model.pickup.PickupPointCheck
import com.kscho.way.domain.model.pickup.PickupZones
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.pickup.PickupDraft
import com.kscho.way.domain.usecase.pickup.CheckPickupPointUseCase
import com.kscho.way.domain.usecase.restriction.GetStopZonesUseCase
import com.kscho.way.presentation.component.base.BaseViewModel
import com.kscho.way.presentation.component.base.UiEvent
import com.kscho.way.presentation.component.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AdjustPinUiState(
  val origin: LatLng,
  val hasPlace: Boolean,
  val passengerName: String?,
  val pin: LatLng,
  val checking: Boolean = true,
  val result: PickupPointCheck? = null,
  val lastBlocked: LatLng? = null,
  val error: String? = null,
  val zones: List<RestrictedZone> = emptyList(),
  val zonesLoading: Boolean = false,
) : UiState

sealed interface AdjustPinEvent : UiEvent {
  data object NavigateToSetDestination : AdjustPinEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class AdjustPinViewModel @Inject constructor(
  private val checkPickupPoint: CheckPickupPointUseCase,
  private val getStopZones: GetStopZonesUseCase,
  private val draft: PickupDraft,
) : BaseViewModel<AdjustPinUiState, AdjustPinEvent>(initialState(draft)) {
  private val place = draft.place.value
  private val loadedZones = MutableStateFlow<List<RestrictedZone>>(emptyList())
  private var loadedArea: MapArea? = null

  init {
    val pins = uiState.map { it.pin }.distinctUntilChanged()
    combine(loadedZones, pins) { all, pin -> PickupZones.around(pin, all) }
      .onEach { zones -> updateState { copy(zones = zones) } }
      .launchIn(viewModelScope)
    viewModelScope.launch {
      pins.conflate().collect { pin ->
        if (PickupZones.needsReload(loadedArea, pin)) loadZones(PickupZones.areaFor(pin))
      }
    }
    viewModelScope.launch { pins.debounce(CHECK_DEBOUNCE_MILLIS).collectLatest(::check) }
  }

  fun setPin(location: LatLng) {
    if (Geo.distanceMeters(location, currentState.pin) < PIN_MOVE_METERS) return
    updateState { copy(pin = location) }
  }

  fun confirm() {
    val state = currentState
    val result = state.result ?: return
    if (state.checking || result.point.location != state.pin) return
    draft.check.value = result
    emitEvent(AdjustPinEvent.NavigateToSetDestination)
  }

  private suspend fun check(checkedPin: LatLng) {
    if (currentState.result?.point?.location == checkedPin) return
    updateState { copy(checking = true) }
    val result = runCatching { checkPickupPoint(checkedPin, place) }
    updateState {
      if (pin != checkedPin) return@updateState this
      result.fold(
        onSuccess = { checked ->
          val wasBlocked = this.result?.takeIf { !it.canConfirm }?.point?.location
          copy(
            checking = false,
            result = checked,
            error = null,
            lastBlocked = if (checked.canConfirm) wasBlocked ?: lastBlocked else lastBlocked,
          )
        },
        onFailure = { copy(checking = false, error = CHECK_FAILED_MESSAGE) },
      )
    }
  }

  private suspend fun loadZones(area: MapArea) {
    updateState { copy(zonesLoading = true) }
    when (val result = getStopZones(area.center, area.radiusMeters)) {
      is ApiState.Success -> {
        loadedZones.value = result.data
        loadedArea = area
      }
      is ApiState.Fail -> Log.w(TAG, "구간을 못 받았어요: ${result.failState.message}")
    }
    updateState { copy(zonesLoading = false) }
  }

  private companion object {
    const val TAG = "WayZones"
    const val CHECK_DEBOUNCE_MILLIS = 350L
    const val PIN_MOVE_METERS = 0.5
    const val CHECK_FAILED_MESSAGE = "위치를 확인하지 못했어요. 핀을 조금 옮겨 다시 시도해 주세요"
  }
}

private val DEFAULT_ORIGIN = LatLng(37.5555, 126.939)

private fun initialState(draft: PickupDraft): AdjustPinUiState {
  val place = draft.place.value
  val origin = draft.pinStart.value ?: place?.location ?: draft.check.value?.point?.location ?: DEFAULT_ORIGIN
  return AdjustPinUiState(
    origin = origin,
    hasPlace = place != null,
    passengerName = draft.passenger.value?.callName,
    pin = draft.check.value?.point?.location ?: origin,
  )
}
