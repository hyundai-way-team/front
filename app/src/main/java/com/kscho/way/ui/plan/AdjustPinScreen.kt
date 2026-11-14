package com.kscho.way.ui.plan

import android.util.Log
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.R
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.MapArea
import com.kscho.way.domain.model.PickupZones
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.StopRestriction
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.repository.StopRestrictionRepository
import com.kscho.way.domain.usecase.CheckPickupPoint
import com.kscho.way.domain.usecase.PickupPointCheck
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
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.common.whiteCard
import com.kscho.way.ui.map.MapLabel
import com.kscho.way.ui.map.MapLine
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
class AdjustPinViewModel(private val check: CheckPickupPoint, private val restrictions: StopRestrictionRepository, private val draft: PickupDraft) : ViewModel() {
  val place: Place? = draft.place.value
  val origin: LatLng = draft.pinStart.value ?: place?.location ?: draft.check.value?.point?.location ?: LatLng(37.5555, 126.939)
  private val _state = MutableStateFlow(AdjustPinUiState(pin = draft.check.value?.point?.location ?: origin))
  val state = _state.asStateFlow()
  /** 받아 둔 정차 금지 구간(핀 둘레보다 넉넉히) — 지도엔 지금 핀 둘레만 그린다([zones]). */
  private val loadedZones = MutableStateFlow<List<RestrictedZone>>(emptyList())
  /** 지도에 그릴 구간 = 지금 핀(픽업 위치) 둘레만(PickupZones). 화면 곳곳의 구간은 그리지 않는다. */
  val zones: StateFlow<List<RestrictedZone>> =
    combine(loadedZones, _state.map { it.pin }.distinctUntilChanged()) { all, pin -> PickupZones.around(pin, all) }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  /** 핀 둘레 정차 금지 구간을 받는 중(지정 금지구역은 서버가 끝점을 실시간으로 찾아 몇 초 걸릴 수 있다). */
  val zonesLoading = MutableStateFlow(false)
  /** 구간을 받아 둔 곳 — 핀을 옮겨 핀 둘레가 이 밖으로 나가면 다시 받는다. */
  private var loadedArea: MapArea? = null

  init {
    viewModelScope.launch {
      // 받는 중인 요청은 끊지 않는다(핀을 연달아 옮겨도) — 그새 쌓인 낡은 자리만 건너뛴다
      _state.map { it.pin }.distinctUntilChanged().conflate().collect { pin ->
        if (!PickupZones.needsReload(loadedArea, pin)) return@collect
        load(PickupZones.areaFor(pin))
      }
    }
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

  /** 지도를 움직여 멈춘 곳(= 화면 가운데 핀). */
  fun setPin(location: LatLng) {
    if (Geo.distanceMeters(location, _state.value.pin) < 0.5) return
    _state.update { it.copy(pin = location) }
  }

  /**
   * 확정. 와이어프레임 v2: 정차 금지 구간이어도 경고만 하고 확정은 운전자가 정한다(D-03 핀 지정 Warning).
   * 확인이 끝난 지금 핀이어야 한다.
   */
  fun confirm(): Boolean {
    val s = _state.value
    val result = s.result ?: return false
    if (s.checking || result.point.location != s.pin) return false
    draft.check.value = result
    return true
  }
}

/** D-03 핀 지정 → D-04 핀 이동 후(와이어프레임 v2): 지도를 움직여 핀을 정차 지점에 맞추고 정차 금지 구간을 확인한다(기획 3-2). */
@Composable
fun AdjustPinScreen(onBack: () -> Unit, onSearchAgain: () -> Unit, onConfirmed: () -> Unit) {
  val c = appContainer()
  val vm = viewModel { AdjustPinViewModel(c.checkPickupPoint, c.restrictions, c.draft) }
  val state by vm.state.collectAsStateWithLifecycle()
  val zones by vm.zones.collectAsStateWithLifecycle()
  val zonesLoading by vm.zonesLoading.collectAsStateWithLifecycle()
  // 금방 끝나는 확인·받기에는 아무것도 띄우지 않는다(깜빡임) — 0.3초 넘게 걸릴 때만 진행 표시
  val showChecking = rememberLingering(state.checking)
  val showZonesLoading = rememberLingering(zonesLoading)
  // 지도는 처음 핀 위치에서 시작하고, 이후엔 운전자가 움직인다
  val startCenter = remember { state.pin }
  val result = state.result?.takeIf { it.point.location == state.pin }
  // 핀을 옮겨 다시 확인하는 동안엔 바로 전 결과를 그대로 보여 준다(카드가 깜빡이지 않게)
  val shown = result ?: state.result
  val blocked = shown?.restriction as? StopRestriction.NoStopping
  val passengerName = c.draft.passenger.value?.callName ?: "동승자"

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
      if (showZonesLoading) {
        // 지도 위 가장자리 얇은 막대 — 구간을 받는 동안 멈춘 게 아니라는 표시(지도 틀 테두리 2dp 아래로)
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

/** [busy] 가 0.3초 넘게 이어질 때만 true — 짧은 확인에 진행 표시가 깜빡이지 않게. */
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

/** 확인 중 상자 — 경고 상자와 같은 모양(라운드 16 · 안쪽 16)에 파란 진행 표시. */
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

/** 픽업 위치 카드: '픽업 위치' · 정차 규제 칩 · 위치 이름(Title/XL). */
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
