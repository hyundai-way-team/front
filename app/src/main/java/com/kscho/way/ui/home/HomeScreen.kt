package com.kscho.way.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.R
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.PickupRecord
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.repository.PickupHistoryRepository
import com.kscho.way.domain.repository.StopRestrictionRepository
import com.kscho.way.domain.vehicle.Navigator
import com.kscho.way.theme.WayColors
import com.kscho.way.theme.WayType
import com.kscho.way.ui.common.ActionButton
import com.kscho.way.ui.common.Avatar
import com.kscho.way.ui.common.MapFrame
import com.kscho.way.ui.common.Panel
import com.kscho.way.ui.common.ScreenFrame
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.common.whiteCard
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(val recent: List<PickupRecord> = emptyList(), val here: LatLng? = null, val zones: List<RestrictedZone> = emptyList())

class HomeViewModel(private val history: PickupHistoryRepository, private val navigator: Navigator, private val restrictions: StopRestrictionRepository) :
  ViewModel() {
  private val _state = MutableStateFlow(HomeUiState())
  val state = _state.asStateFlow()

  fun refresh() {
    viewModelScope.launch {
      runCatching { history.recent(limit = 3) }.onSuccess { list -> _state.update { it.copy(recent = list) } }
      val here = runCatching { navigator.currentLocation() }.getOrNull() ?: return@launch
      _state.update { it.copy(here = here) }
      runCatching { restrictions.zonesNear(here, 600) }.onSuccess { z -> _state.update { it.copy(zones = z) } }
    }
  }
}

/** D-01 홈(와이어프레임 v2): 픽업 만들기 · 최근 픽업 · 현재 위치 지도. */
@Composable
fun HomeScreen(onCreatePickup: () -> Unit, onReuse: (PickupRecord) -> Unit) {
  val c = appContainer()
  val vm = viewModel { HomeViewModel(c.history, c.navigator, c.restrictions) }
  val state by vm.state.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { vm.refresh() }

  ScreenFrame {
    Panel(width = 480.dp) {
      Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
              // 로고 자리(디자인 임시) — 워드마크에 'Where are you'가 들어갈 예정
              Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(WayColors.Placeholder))
              Text("WAY", style = WayType.HeadingM, color = WayColors.Primary500)
            }
            Text("정확한 픽업 위치를 정하고 출발하세요", style = WayType.LMedium, color = WayColors.Grey500)
          }
          ActionButton("픽업 만들기", onCreatePickup, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("최근 픽업", style = WayType.LSemiBold, color = WayColors.Grey700)
            Text("${state.recent.size}건", style = WayType.LMedium, color = WayColors.Primary500)
          }
          if (state.recent.isEmpty()) {
            Text("아직 픽업 기록이 없어요", style = WayType.MRegular, color = WayColors.Grey500)
          }
          LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(state.recent, key = { it.id }) { record -> RecentPickupCard(record) { onReuse(record) } }
          }
        }
      }
    }
    MapFrame {
      val here = state.here
      if (here != null) {
        WayMap(
          center = here,
          metersPerDp = 1.2f,
          modifier = Modifier.fillMaxSize(),
          zones = state.zones,
          pins = listOf(MapPin(here, PinKind.CAR, "현재 위치")),
          caption = "지도 · 현재 위치",
          interactive = true,
        )
      } else {
        Box(Modifier.fillMaxSize().background(WayColors.MapBg))
      }
    }
  }
}

/** 최근 픽업 카드 — 누르면 같은 사람·같은 위치로 다시(기획 12). */
@Composable
private fun RecentPickupCard(record: PickupRecord, onClick: () -> Unit) {
  val press = remember { MutableInteractionSource() }
  val pressed by press.collectIsPressedAsState()
  Row(
    Modifier.fillMaxWidth()
      .whiteCard(pressed)
      .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Avatar()
      Column {
        Text(record.passenger.name, style = WayType.TitleL, color = Color.Black)
        Text(record.pickupPoint.description.title, style = WayType.MRegular, color = WayColors.Grey500)
      }
    }
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
  }
}
