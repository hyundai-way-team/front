package com.kscho.way.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kscho.way.R
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.model.vehicle.MyVehicle
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.repository.vehicle.VehicleProfileRepository
import com.kscho.way.theme.WayColors
import com.kscho.way.theme.WayType
import com.kscho.way.ui.common.ActionButton
import com.kscho.way.ui.common.AlertBox
import com.kscho.way.ui.common.BackButton
import com.kscho.way.ui.common.Format
import com.kscho.way.ui.common.MapFrame
import com.kscho.way.ui.common.Panel
import com.kscho.way.ui.common.ScreenFrame
import com.kscho.way.ui.common.StepDots
import com.kscho.way.ui.common.Tone
import com.kscho.way.ui.map.MapLine
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConfirmUiState(val vehicle: MyVehicle? = null, val starting: Boolean = false, val error: String? = null)

@HiltViewModel
class ConfirmDepartureViewModel @Inject constructor(
  private val vehicles: VehicleProfileRepository,
  private val coordinator: PickupCoordinator,
  private val draft: PickupDraft,
) : ViewModel() {
  val passenger = draft.passenger.value
  val place = draft.place.value
  val check = draft.check.value
  val destination = draft.destination.asStateFlow()
  private val _state = MutableStateFlow(ConfirmUiState())
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      runCatching { vehicles.myVehicle() }
        .onSuccess { v -> _state.value = _state.value.copy(vehicle = v) }
        .onFailure { _state.value = _state.value.copy(error = "내 차 정보를 불러오지 못했어요") }
    }
  }

  fun clearDestination() {
    draft.destination.value = null
  }

  fun start(onStarted: () -> Unit) {
    val passenger = passenger ?: return
    val check = check ?: return
    val vehicle = _state.value.vehicle ?: return
    if (_state.value.starting) return
    _state.value = _state.value.copy(starting = true, error = null)
    viewModelScope.launch {
      try {
        coordinator.start(PickupPlan(passenger, place, check.point, check.restriction, check.route, vehicle, destination.value))
        draft.reset()
        onStarted()
      } catch (e: Exception) {
        _state.value = _state.value.copy(starting = false, error = e.message ?: "출발하지 못했어요")
      }
    }
  }
}

@Composable
fun ConfirmDepartureScreen(onBack: () -> Unit, onStarted: () -> Unit) {
  val vm: ConfirmDepartureViewModel = hiltViewModel()
  val state by vm.state.collectAsStateWithLifecycle()
  val destination by vm.destination.collectAsStateWithLifecycle()
  val passenger = vm.passenger
  val check = vm.check
  if (passenger == null || check == null) {
    LaunchedEffect(Unit) { onBack() }
    return
  }
  val now = System.currentTimeMillis()
  val arrival = now + check.route.durationSeconds * 1000L

  ScreenFrame {
    Panel(width = 560.dp) {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.fillMaxWidth()) {
          BackButton(onBack)
          Box(Modifier.align(Alignment.CenterEnd)) { StepDots(step = 3, lineWidth = 40.dp) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
          Text("이대로 출발할까요?", style = WayType.HeadingM, color = Color.Black)
          Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(
              Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, WayColors.Primary500, RoundedCornerShape(16.dp)).padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              InfoRow("동승자") { Text("${passenger.name} (${passenger.displayMaskedPhone})", style = WayType.TitleM, color = WayColors.Grey900) }
              HorizontalDivider(color = WayColors.Grey200)
              InfoRow("픽업 위치") { Text(check.point.description.title, style = WayType.TitleM, color = WayColors.Grey900) }
              HorizontalDivider(color = WayColors.Grey200)
              InfoRow("도착 예정") {
                Text("${Format.clock(arrival)} (${Format.duration(check.route.durationSeconds)})", style = WayType.TitleM, color = WayColors.Primary500)
              }
              state.vehicle?.let { v ->
                HorizontalDivider(color = WayColors.Grey200)
                InfoRow("내 차") { Text("${v.description} | ${v.plateNumber}", style = WayType.TitleM, color = WayColors.Grey900) }
              }
              destination?.let { d ->
                HorizontalDivider(color = WayColors.Grey200)
                InfoRow("목적지") {
                  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name, style = WayType.TitleM, color = WayColors.Grey900, modifier = Modifier.weight(1f))
                    Icon(
                      painterResource(R.drawable.ic_close),
                      contentDescription = "목적지 지우기",
                      tint = Color.Unspecified,
                      modifier = Modifier.size(24.dp).clickable(role = Role.Button, onClick = vm::clearDestination),
                    )
                  }
                }
              }
            }
            state.error?.let { AlertBox(it, Tone.ERROR) }
          }
        }
      }
      ActionButton(
        if (state.starting) "출발 준비 중…" else "출발",
        { vm.start(onStarted) },
        Modifier.fillMaxWidth().padding(top = 16.dp),
        enabled = state.vehicle != null && !state.starting,
      )
    }
    MapFrame {
      RoutePreviewMap(check.route.path.ifEmpty { listOf(check.point.location) }, check.point.location, Modifier.fillMaxSize())
    }
  }
}

@Composable
private fun InfoRow(label: String, content: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
    Text(label, style = WayType.LMedium, color = WayColors.Grey500, modifier = Modifier.width(66.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
  }
}

@Composable
private fun RoutePreviewMap(path: List<LatLng>, pickup: LatLng, modifier: Modifier) {
  BoxWithConstraints(modifier) {
    val lats = path.map { it.latitude } + pickup.latitude
    val lngs = path.map { it.longitude } + pickup.longitude
    val center = LatLng((lats.min() + lats.max()) / 2, (lngs.min() + lngs.max()) / 2)
    val (width, height) = Geo.offsetMeters(LatLng(lats.min(), lngs.min()), LatLng(lats.max(), lngs.max()))
    val metersPerDp = max(max(width / (maxWidth.value * 0.75), height / (maxHeight.value * 0.75)), 0.3).toFloat()
    WayMap(
      center = center,
      metersPerDp = metersPerDp,
      modifier = Modifier.fillMaxSize(),
      lines = listOf(MapLine(path, WayColors.Blue, 7f)),
      pins = listOfNotNull(path.firstOrNull()?.takeIf { path.size > 1 }?.let { MapPin(it, PinKind.CAR, "내 차 · 출발 지점") }, MapPin(pickup, PinKind.PICKUP, "픽업 위치")),
      caption = "지도 · 픽업 위치까지 경로 미리보기",
      interactive = true,
    )
  }
}
