package com.kscho.way.ui.plan

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kscho.way.R
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.geo.MapArea
import com.kscho.way.domain.model.pickup.PickupPointCheck
import com.kscho.way.domain.model.pickup.PickupZones
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import com.kscho.way.domain.usecase.pickup.CheckPickupPointUseCase
import com.kscho.way.theme.WayColors
import com.kscho.way.theme.WayType
import com.kscho.way.ui.common.ActionButton
import com.kscho.way.ui.common.ActionKind
import com.kscho.way.ui.common.AlertBox
import com.kscho.way.ui.common.BackButton
import com.kscho.way.ui.common.BulletBox
import com.kscho.way.ui.common.MapFrame
import com.kscho.way.ui.common.Panel
import com.kscho.way.ui.common.ScreenFrame
import com.kscho.way.ui.common.StatusChip
import com.kscho.way.ui.common.StepDots
import com.kscho.way.ui.common.Tone
import com.kscho.way.ui.common.whiteCard
import com.kscho.way.ui.map.MapLabel
import com.kscho.way.ui.map.MapLine
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdjustPinUiState(
  val pin: LatLng,
  val checking: Boolean = true,
  val result: PickupPointCheck? = null,
  val lastBlocked: LatLng? = null,
  val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class AdjustPinViewModel @Inject constructor(private val check: CheckPickupPointUseCase, private val restrictions: StopRestrictionRepository, private val draft: PickupDraft) : ViewModel() {
  val place: Place? = draft.place.value
  val passengerName: String? = draft.passenger.value?.callName
  val origin: LatLng = draft.pinStart.value ?: place?.location ?: draft.check.value?.point?.location ?: LatLng(37.5555, 126.939)
  private val _state = MutableStateFlow(AdjustPinUiState(pin = draft.check.value?.point?.location ?: origin))
  val state = _state.asStateFlow()
  private val loadedZones = MutableStateFlow<List<RestrictedZone>>(emptyList())
  val zones: StateFlow<List<RestrictedZone>> =
    combine(loadedZones, _state.map { it.pin }.distinctUntilChanged()) { all, pin -> PickupZones.around(pin, all) }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val zonesLoading = MutableStateFlow(false)
  private var loadedArea: MapArea? = null

  init {
    viewModelScope.launch {
      _state.map { it.pin }.distinctUntilChanged().conflate().collect { pin ->
        if (!PickupZones.needsReload(loadedArea, pin)) return@collect
        load(PickupZones.areaFor(pin))
      }
    }
    viewModelScope.launch {
      _state.map { it.pin }.distinctUntilChanged().debounce(350).collectLatest { pin ->
        if (_state.value.result?.point?.location == pin) return@collectLatest
        _state.update { it.copy(checking = true) }
        val result = runCatching { check(pin, place) }
        _state.update { now ->
          if (now.pin != pin) return@update now
          result.fold(
            onSuccess = { r ->
              val wasBlocked = now.result?.takeIf { !it.canConfirm }?.point?.location
              now.copy(checking = false, result = r, error = null, lastBlocked = if (r.canConfirm) wasBlocked ?: now.lastBlocked else now.lastBlocked)
            },
            onFailure = { now.copy(checking = false, error = "위치를 확인하지 못했어요. 핀을 조금 옮겨 다시 시도해 주세요") },
          )
        }
      }
    }
  }

  private suspend fun load(area: MapArea) {
    zonesLoading.value = true
    runCatching { restrictions.zonesNear(area.center, area.radiusMeters) }
      .onSuccess {
        loadedZones.value = it
        loadedArea = area
      }
      .onFailure { Log.w("WayZones", "구간을 못 받았어요", it) }
    zonesLoading.value = false
  }

  fun setPin(location: LatLng) {
    if (Geo.distanceMeters(location, _state.value.pin) < 0.5) return
    _state.update { it.copy(pin = location) }
  }

  fun confirm(): Boolean {
    val s = _state.value
    val result = s.result ?: return false
    if (s.checking || result.point.location != s.pin) return false
    draft.check.value = result
    return true
  }
}

@Composable
fun AdjustPinScreen(onBack: () -> Unit, onSearchAgain: () -> Unit, onConfirmed: () -> Unit) {
  val vm: AdjustPinViewModel = hiltViewModel()
  val state by vm.state.collectAsStateWithLifecycle()
  val zones by vm.zones.collectAsStateWithLifecycle()
  val zonesLoading by vm.zonesLoading.collectAsStateWithLifecycle()
  val showChecking = rememberLingering(state.checking)
  val showZonesLoading = rememberLingering(zonesLoading)
  val startCenter = remember { state.pin }
  val result = state.result?.takeIf { it.point.location == state.pin }
  val shown = result ?: state.result
  val blocked = shown?.restriction as? StopRestriction.NoStopping
  val passengerName = vm.passengerName ?: "동승자"

  ScreenFrame {
    Panel(width = 560.dp) {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.fillMaxWidth()) {
          BackButton(onBack)
          Box(Modifier.align(Alignment.CenterEnd)) { StepDots(step = 2, lineWidth = 40.dp) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("차량을 세울 곳에 핀을 맞춰 주세요", style = WayType.HeadingM, color = Color.Black)
            Text("약속 장소가 곧 픽업 위치예요. 도로의 어느 쪽인지까지 맞춰 주세요.", style = WayType.LMedium, color = WayColors.Grey500)
          }
          Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (shown != null) PickupPointCard(shown.point.description.title, blocked != null)
            BulletBox(listOf("이 좌표가 $passengerName 님 화면에 그대로 보여요", "버스정류장, 횡단보도 앞은 피해 주세요"))
            when {
              state.error != null -> AlertBox(state.error!!, Tone.ERROR)
              state.checking && (showChecking || shown == null) -> CheckingBox("정차 금지 구간을 확인하고 있어요")
              shown == null -> Unit
              blocked != null -> AlertBox("도착 예정 시각에 정차 금지 구간이에요. 핀을 옮겨주세요.", Tone.ERROR)
              else -> AlertBox("데이터에 없는 규제도 있어요. 현장 표지판을 확인해 주세요.", Tone.WARNING)
            }
          }
        }
      }
      Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButton("위치 확정", { if (vm.confirm()) onConfirmed() }, Modifier.weight(1f), enabled = result != null && !state.checking)
        ActionButton("장소 재검색", onSearchAgain, Modifier.weight(1f), kind = ActionKind.OUTLINE)
      }
    }
    MapFrame {
      val moved = Geo.distanceMeters(vm.origin, state.pin) > 3.0
      WayMap(
        center = startCenter,
        metersPerDp = 0.35f,
        modifier = Modifier.fillMaxSize(),
        zones = zones,
        lines = if (moved) listOf(MapLine(listOf(vm.origin, state.pin), WayColors.Blue, 2f, dashed = true)) else emptyList(),
        pins =
          buildList {
            if (moved && vm.place != null) add(MapPin(vm.origin, PinKind.GHOST, "처음 핀 (약속 장소)"))
            state.lastBlocked?.takeIf { blocked == null && Geo.distanceMeters(it, state.pin) > 3.0 }?.let { add(MapPin(it, PinKind.GHOST_BLOCKED, "이전 핀 · 정차 금지")) }
          },
        caption = "지도 · 지도를 움직여 핀을 정차 지점에 맞춰요",
        showZoneLegend = true,
        interactive = true,
        followCamera = false,
        centerPin =
          if (blocked != null) MapPin(state.pin, PinKind.PICKUP_BLOCKED, "픽업 위치")
          else MapPin(state.pin, PinKind.PICKUP, "픽업 위치"),
        onCenterSettled = vm::setPin,
        showZoomButtons = true,
      )
      val hint =
        when {
          blocked != null -> "여기는 ${blocked.zone.name} 쪽 정차 금지 구간이에요"
          state.lastBlocked != null && result != null -> "정차 금지 구간을 벗어났어요"
          else -> null
        }
      hint?.let { MapLabel(it, Modifier.align(Alignment.BottomStart).padding(20.dp)) }
      if (showZonesLoading) {
        LinearProgressIndicator(
          Modifier.align(Alignment.TopCenter).padding(top = 2.dp).fillMaxWidth().height(4.dp),
          color = WayColors.Primary500,
          trackColor = WayColors.Primary100,
        )
        if (hint == null) MapLabel("정차 금지 구간 불러오는 중", Modifier.align(Alignment.BottomStart).padding(20.dp))
      }
    }
  }
}

@Composable
private fun rememberLingering(busy: Boolean): Boolean {
  var show by remember { mutableStateOf(false) }
  LaunchedEffect(busy) {
    if (busy) {
      delay(300)
      show = true
    } else {
      show = false
    }
  }
  return busy && show
}

private val BOX_SHAPE = RoundedCornerShape(16.dp)

@Composable
private fun CheckingBox(text: String) {
  Row(
    Modifier.fillMaxWidth().clip(BOX_SHAPE).background(WayColors.Primary50).border(1.dp, WayColors.Primary100, BOX_SHAPE).padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(Modifier.size(20.dp), color = WayColors.Primary500, trackColor = WayColors.Primary100, strokeWidth = 2.5.dp)
    }
    Text(text, style = WayType.LRegular, color = WayColors.Primary500, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun PickupPointCard(title: String, blocked: Boolean) {
  Column(Modifier.fillMaxWidth().whiteCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_place), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
        Text("픽업 위치", style = WayType.LRegular, color = WayColors.Primary500)
      }
      if (blocked) StatusChip("정차 금지 구간", Tone.ERROR) else StatusChip("정차 규제 미확인", Tone.NEUTRAL)
    }
    Text(title, style = WayType.TitleXL, color = WayColors.Grey900)
  }
}
