package com.kscho.way.presentation.plan

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.presentation.component.base.CollectUiEvents
import com.kscho.way.presentation.component.common.ActionButton
import com.kscho.way.presentation.component.common.ActionKind
import com.kscho.way.presentation.component.common.AlertBox
import com.kscho.way.presentation.component.common.BackButton
import com.kscho.way.presentation.component.common.BulletBox
import com.kscho.way.presentation.component.common.MapFrame
import com.kscho.way.presentation.component.common.Panel
import com.kscho.way.presentation.component.common.ScreenFrame
import com.kscho.way.presentation.component.common.StatusChip
import com.kscho.way.presentation.component.common.StepDots
import com.kscho.way.presentation.component.common.Tone
import com.kscho.way.presentation.component.common.whiteCard
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayTheme
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.map.MapLabel
import com.kscho.way.presentation.map.MapLine
import com.kscho.way.presentation.map.MapPin
import com.kscho.way.presentation.map.PinKind
import com.kscho.way.presentation.map.WayMap
import com.kscho.way.presentation.plan.R
import kotlinx.coroutines.delay

@Composable
fun AdjustPinRoute(
  onBack: () -> Unit,
  onSearchAgain: () -> Unit,
  onNavigateToSetDestination: () -> Unit,
  viewModel: AdjustPinViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  CollectUiEvents(viewModel.uiEvent) { event ->
    when (event) {
      AdjustPinEvent.NavigateToSetDestination -> onNavigateToSetDestination()
    }
  }
  AdjustPinScreen(
    state = state,
    onBack = onBack,
    onSearchAgain = onSearchAgain,
    onConfirm = viewModel::confirm,
    onPinSettled = viewModel::setPin,
  )
}

@Composable
fun AdjustPinScreen(
  state: AdjustPinUiState,
  onBack: () -> Unit,
  onSearchAgain: () -> Unit,
  onConfirm: () -> Unit,
  onPinSettled: (LatLng) -> Unit,
) {
  val showChecking = rememberLingering(state.checking)
  val showZonesLoading = rememberLingering(state.zonesLoading)
  val startCenter = remember { state.pin }
  val result = state.result?.takeIf { it.point.location == state.pin }
  val shown = result ?: state.result
  val blocked = shown?.restriction as? StopRestriction.NoStopping
  val passengerName = state.passengerName ?: "동승자"

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
        ActionButton("위치 확정", onConfirm, Modifier.weight(1f), enabled = result != null && !state.checking)
        ActionButton("장소 재검색", onSearchAgain, Modifier.weight(1f), kind = ActionKind.OUTLINE)
      }
    }
    MapFrame {
      val moved = Geo.distanceMeters(state.origin, state.pin) > MOVED_METERS
      WayMap(
        center = startCenter,
        metersPerDp = 0.35f,
        modifier = Modifier.fillMaxSize(),
        zones = state.zones,
        lines = if (moved) listOf(MapLine(listOf(state.origin, state.pin), WayColors.Blue, 2f, dashed = true)) else emptyList(),
        pins =
          buildList {
            if (moved && state.hasPlace) add(MapPin(state.origin, PinKind.GHOST, "처음 핀 (약속 장소)"))
            state.lastBlocked?.takeIf { blocked == null && Geo.distanceMeters(it, state.pin) > MOVED_METERS }?.let { add(MapPin(it, PinKind.GHOST_BLOCKED, "이전 핀 · 정차 금지")) }
          },
        caption = "지도 · 지도를 움직여 핀을 정차 지점에 맞춰요",
        showZoneLegend = true,
        interactive = true,
        followCamera = false,
        centerPin =
          if (blocked != null) MapPin(state.pin, PinKind.PICKUP_BLOCKED, "픽업 위치")
          else MapPin(state.pin, PinKind.PICKUP, "픽업 위치"),
        onCenterSettled = onPinSettled,
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

@Preview(widthDp = 1280, heightDp = 720)
@Composable
internal fun AdjustPinScreenPreview() {
  val pin = LatLng(37.5571, 126.942)
  WayTheme {
    AdjustPinScreen(
      state = AdjustPinUiState(origin = pin, hasPlace = true, passengerName = "서연", pin = pin, checking = false),
      onBack = {},
      onSearchAgain = {},
      onConfirm = {},
      onPinSettled = {},
    )
  }
}

private const val MOVED_METERS = 3.0
