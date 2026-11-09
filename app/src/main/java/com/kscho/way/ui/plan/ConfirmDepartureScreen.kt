package com.kscho.way.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.MyVehicle
import com.kscho.way.domain.model.PickupPlan
import com.kscho.way.domain.model.StopRestriction
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.repository.VehicleProfileRepository
import com.kscho.way.theme.WayColors
import com.kscho.way.ui.common.Format
import com.kscho.way.ui.common.PrimaryButton
import com.kscho.way.ui.common.StepHeader
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.map.MapLine
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConfirmUiState(val vehicle: MyVehicle? = null, val starting: Boolean = false, val error: String? = null)

class ConfirmDepartureViewModel(
  private val vehicles: VehicleProfileRepository,
  private val coordinator: PickupCoordinator,
  private val draft: PickupDraft,
) : ViewModel() {
  val passenger = draft.passenger.value
  val place = draft.place.value
  val check = draft.check.value
  private val _state = MutableStateFlow(ConfirmUiState())
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      runCatching { vehicles.myVehicle() }
        .onSuccess { v -> _state.value = _state.value.copy(vehicle = v) }
        .onFailure { _state.value = _state.value.copy(error = "내 차 정보를 불러오지 못했어요") }
    }
  }

  /** 출발: 서버에 픽업을 만들고(문자 발송) 내비 목적지를 픽업 위치로(기획 3-3). */
  fun start(onStarted: () -> Unit) {
    val passenger = passenger ?: return
    val check = check ?: return
    val vehicle = _state.value.vehicle ?: return
    if (_state.value.starting) return
    _state.value = _state.value.copy(starting = true, error = null)
    viewModelScope.launch {
      try {
        coordinator.start(PickupPlan(passenger, place, check.point, check.restriction, check.route, vehicle))
        draft.reset()
        onStarted()
      } catch (e: Exception) {
        _state.value = _state.value.copy(starting = false, error = e.message ?: "출발하지 못했어요")
      }
    }
  }
}

/** D-05 확인 · 출발. 출발 뒤에는 운전자 화면 조작이 없다. */
@Composable
fun ConfirmDepartureScreen(onBack: () -> Unit, onStarted: () -> Unit) {
  val c = appContainer()
  val vm = viewModel { ConfirmDepartureViewModel(c.vehicleProfile, c.coordinator, c.draft) }
  val state by vm.state.collectAsStateWithLifecycle()
  val passenger = vm.passenger
  val check = vm.check
  if (passenger == null || check == null) {
    LaunchedEffect(Unit) { onBack() }
    return
  }
  val now = System.currentTimeMillis()
  val arrival = now + check.route.durationSeconds * 1000L

  Row(Modifier.fillMaxSize().background(Color.White)) {
    Column(Modifier.width(560.dp).fillMaxHeight().padding(40.dp)) {
      StepHeader(step = 3, onBack = onBack)
      Spacer(Modifier.height(20.dp))
      Text("이대로 출발할까요?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
      Spacer(Modifier.height(16.dp))
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, WayColors.Line, RoundedCornerShape(10.dp))) {
        SummaryRow("동승자") { Text("${passenger.name} · ${passenger.maskedPhone}", fontSize = 19.sp, color = WayColors.Ink) }
        HorizontalDivider(color = WayColors.Chip)
        SummaryRow("픽업 위치") {
          Text(check.point.description.title, fontSize = 19.sp, color = WayColors.Ink)
          Text(check.point.description.detail, fontSize = 14.sp, color = WayColors.Sub)
        }
        HorizontalDivider(color = WayColors.Chip)
        SummaryRow("규제 확인") {
          val blocked = check.restriction as? StopRestriction.NoStopping
          Text(if (blocked == null) "● 정차 금지 구간 확인 안 됨" else "● ${blocked.zone.name} 정차 금지", fontSize = 19.sp, color = if (blocked == null) WayColors.Ink else WayColors.Red)
          Text("${Format.clock(check.arrivalAtMillis)} 도착 기준 · 현장 표지판을 확인해 주세요", fontSize = 14.sp, color = WayColors.Sub)
        }
        HorizontalDivider(color = WayColors.Chip)
        SummaryRow("도착 예정") {
          Text("${Format.clock(arrival)} · ${Format.duration(check.route.durationSeconds)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WayColors.Blue)
        }
        HorizontalDivider(color = WayColors.Chip)
        SummaryRow("내 차") { Text(state.vehicle?.label ?: "불러오는 중…", fontSize = 19.sp, color = WayColors.Ink) }
      }
      Spacer(Modifier.height(16.dp))
      Text(
        buildAnnotatedString {
          append("출발하면 ${passenger.callName} 님에게 Pleos 문자로 ")
          withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = WayColors.Ink)) { append("차량 번호 · 픽업 위치 · 도착 예정 시간 · 참여 링크") }
          append("를 보내요. 이후에는 화면을 만지지 않아도 돼요.")
        },
        fontSize = 15.sp,
        color = WayColors.Sub,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WayColors.Panel).padding(16.dp),
      )
      state.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, fontSize = 15.sp, color = WayColors.Red)
      }
      Spacer(Modifier.weight(1f))
      PrimaryButton(if (state.starting) "출발 준비 중…" else "출발", { vm.start(onStarted) }, Modifier.fillMaxWidth(), enabled = state.vehicle != null && !state.starting)
    }
    RoutePreviewMap(check.route.path.ifEmpty { listOf(check.point.location) }, check.point.location, check.route.distanceMeters, check.route.durationSeconds, Modifier.weight(1f).fillMaxHeight())
  }
}

@Composable
private fun SummaryRow(label: String, content: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
    Text(label, fontSize = 15.sp, color = WayColors.Sub, modifier = Modifier.width(100.dp))
    Column(Modifier.weight(1f)) { content() }
  }
}

/** 경로 전체가 들어오게 맞춘 미리보기 지도. */
@Composable
private fun RoutePreviewMap(path: List<LatLng>, pickup: LatLng, meters: Int, seconds: Int, modifier: Modifier) {
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
    )
    Text(
      "${Format.distance(meters)} · ${Format.minutes(seconds)}",
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = Color.White,
      modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).clip(RoundedCornerShape(16.dp)).background(WayColors.Dark).padding(horizontal = 14.dp, vertical = 6.dp),
    )
  }
}
