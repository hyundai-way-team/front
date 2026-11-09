package com.kscho.way.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.MapFeatures
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.StopRestriction
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.usecase.CheckPickupPoint
import com.kscho.way.domain.usecase.PickupPointCheck
import com.kscho.way.theme.WayColors
import com.kscho.way.ui.common.DotChip
import com.kscho.way.ui.common.Format
import com.kscho.way.ui.common.NoteBox
import com.kscho.way.ui.common.PrimaryButton
import com.kscho.way.ui.common.SecondaryButton
import com.kscho.way.ui.common.StepHeader
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.map.MapLabel
import com.kscho.way.ui.map.MapLine
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdjustPinUiState(
  val pin: LatLng,
  val checking: Boolean = true,
  val result: PickupPointCheck? = null,
  /** 바로 전에 정차 금지로 나온 핀(지도에 흐리게). */
  val lastBlocked: LatLng? = null,
  val error: String? = null,
)

@OptIn(FlowPreview::class)
class AdjustPinViewModel(private val check: CheckPickupPoint, private val places: PlaceRepository, private val draft: PickupDraft) : ViewModel() {
  val place: Place? = draft.place.value
  val origin: LatLng = draft.pinStart.value ?: place?.location ?: draft.check.value?.point?.location ?: LatLng(37.5555, 126.939)
  private val _state = MutableStateFlow(AdjustPinUiState(pin = draft.check.value?.point?.location ?: origin))
  val state = _state.asStateFlow()
  val features = MutableStateFlow<MapFeatures?>(null)

  init {
    viewModelScope.launch { features.value = runCatching { places.mapFeatures(origin, 500) }.getOrNull() }
    viewModelScope.launch {
      // 핀이 멈추면 확인(진행 표시가 바뀌어도 다시 묻지 않게 좌표만 본다)
      _state.map { it.pin }.distinctUntilChanged().debounce(350).collectLatest { pin ->
        if (_state.value.result?.point?.location == pin) return@collectLatest
        _state.update { it.copy(checking = true) }
        val result = runCatching { check(pin, place) }
        _state.update { now ->
          if (now.pin != pin) return@update now // 그새 또 옮겼다
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

  /** 지도를 움직여 멈춘 곳(= 화면 가운데 핀). */
  fun setPin(location: LatLng) {
    if (Geo.distanceMeters(location, _state.value.pin) < 0.5) return
    _state.update { it.copy(pin = location) }
  }

  /** 정차 금지가 아니면 확정. */
  fun confirm(): Boolean {
    val s = _state.value
    val result = s.result ?: return false
    if (s.checking || result.point.location != s.pin || !result.canConfirm) return false
    draft.check.value = result
    return true
  }
}

/** D-04 핀을 정차 지점에 맞추기 · 정차 금지 경고(기획 3-2). */
@Composable
fun AdjustPinScreen(onBack: () -> Unit, onSearchAgain: () -> Unit, onConfirmed: () -> Unit) {
  val c = appContainer()
  val vm = viewModel { AdjustPinViewModel(c.checkPickupPoint, c.places, c.draft) }
  val state by vm.state.collectAsStateWithLifecycle()
  val features by vm.features.collectAsStateWithLifecycle()
  // 지도는 처음 핀 위치에서 시작하고, 이후엔 운전자가 움직인다
  val startCenter = remember { state.pin }
  val result = state.result?.takeIf { it.point.location == state.pin }
  val blocked = result?.restriction as? StopRestriction.NoStopping
  val passengerName = c.draft.passenger.value?.callName ?: "동승자"

  Row(Modifier.fillMaxSize().background(Color.White)) {
    Column(Modifier.width(560.dp).fillMaxHeight().padding(40.dp)) {
      StepHeader(step = 2, onBack = onBack)
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(20.dp))
        Text("차를 세울 곳에 핀을 맞춰 주세요", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
        Text("약속 장소가 곧 픽업 위치예요. 도로의 어느 쪽인지까지 맞춰 주세요.", fontSize = 16.sp, color = WayColors.Sub)
        Spacer(Modifier.height(16.dp))
        Column(
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WayColors.BlueBg)
            .border(2.dp, WayColors.Blue, RoundedCornerShape(10.dp))
            .padding(20.dp)
        ) {
          Text("픽업 위치", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WayColors.Blue)
          Spacer(Modifier.height(6.dp))
          if (result == null) {
            Text(if (state.error != null) state.error!! else "위치 확인 중…", fontSize = 18.sp, color = WayColors.Sub)
          } else {
            Text(result.point.description.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
            Text(result.point.description.detail, fontSize = 16.sp, color = WayColors.Ink)
            result.point.distanceFromPlaceMeters?.takeIf { it >= 5 }?.let {
              Text("검색한 약속 장소에서 ${Format.distance(it)}", fontSize = 15.sp, color = WayColors.Sub)
            }
            Spacer(Modifier.height(8.dp))
            val basis = "${Format.clock(result.arrivalAtMillis)} 도착 기준"
            if (blocked != null) {
              DotChip("정차 금지 구간 · $basis", WayColors.Red, WayColors.RedBg, WayColors.Red)
            } else {
              DotChip("정차 금지 구간 확인 안 됨 · $basis", WayColors.Sub, WayColors.Chip, WayColors.Ink)
            }
          }
        }
        Spacer(Modifier.height(16.dp))
        NoteBox(
          buildList {
            add("이 좌표가 $passengerName 님 화면에 그대로 보여요")
            add("버스정류장 · 횡단보도 앞은 피해 주세요")
            if (result != null && blocked == null) add("데이터에 없는 규제도 있어요. 현장 표지판을 확인해 주세요")
          }
        )
        if (blocked != null && result != null) {
          Spacer(Modifier.height(16.dp))
          Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WayColors.RedBg).border(1.dp, WayColors.Red, RoundedCornerShape(8.dp)).padding(16.dp)
          ) {
            Text("⚠ 도착 예정 시각(${Format.clock(result.arrivalAtMillis)})에 정차 금지 구간이에요.", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WayColors.Red)
            Text("${blocked.zone.name} · ${blocked.zone.rule}. 핀을 옮겨 주세요.", fontSize = 16.sp, color = WayColors.Red)
          }
        }
      }
      Spacer(Modifier.height(16.dp))
      PrimaryButton(
        "이 위치로 확정",
        { if (vm.confirm()) onConfirmed() },
        Modifier.fillMaxWidth(),
        enabled = result != null && result.canConfirm && !state.checking,
      )
      Spacer(Modifier.height(12.dp))
      SecondaryButton("장소 다시 검색", onSearchAgain, Modifier.fillMaxWidth())
    }
    Box(Modifier.weight(1f).fillMaxHeight()) {
      val moved = Geo.distanceMeters(vm.origin, state.pin) > 3.0
      WayMap(
        center = startCenter,
        metersPerDp = 0.35f,
        modifier = Modifier.fillMaxSize(),
        features = features,
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
          if (blocked != null) MapPin(state.pin, PinKind.PICKUP_BLOCKED, "픽업 위치 · 정차 금지 구간")
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
    }
  }
}
