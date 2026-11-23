package com.kscho.way.presentation.home

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPoint
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.presentation.component.base.CollectUiEvents
import com.kscho.way.presentation.component.common.ActionButton
import com.kscho.way.presentation.component.common.MapFrame
import com.kscho.way.presentation.component.common.Panel
import com.kscho.way.presentation.component.common.ScreenFrame
import com.kscho.way.presentation.component.common.whiteCard
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayTheme
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.map.MapPin
import com.kscho.way.presentation.map.PinKind
import com.kscho.way.presentation.map.WayMap

@Composable
fun HomeRoute(
  onNavigateToChoosePassenger: () -> Unit,
  onNavigateToAdjustPin: () -> Unit,
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { viewModel.refresh() }
  CollectUiEvents(viewModel.uiEvent) { event ->
    when (event) {
      HomeEvent.NavigateToChoosePassenger -> onNavigateToChoosePassenger()
      HomeEvent.NavigateToAdjustPin -> onNavigateToAdjustPin()
    }
  }
  HomeScreen(state = state, onCreatePickup = viewModel::createPickup, onReuse = viewModel::reuse)
}

@Composable
fun HomeScreen(state: HomeUiState, onCreatePickup: () -> Unit, onReuse: (PickupRecord) -> Unit) {
  ScreenFrame {
    Panel(width = 480.dp) {
      Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(48.dp)) {
        Image(painterResource(R.drawable.ic_logo_way), contentDescription = "WAY · Where are you")
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
      ActionButton("픽업 만들기", onCreatePickup, Modifier.fillMaxWidth().padding(top = 16.dp), icon = R.drawable.ic_plus)
    }
    MapFrame {
      val here = state.here
      if (here != null) {
        WayMap(
          center = here,
          metersPerDp = 1.2f,
          modifier = Modifier.fillMaxSize(),
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
    Column(Modifier.weight(1f)) {
      Text(record.passenger.name, style = WayType.TitleL, color = Color.Black)
      Text(record.pickupPoint.description.title, style = WayType.MRegular, color = WayColors.Grey500)
    }
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
  }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
internal fun HomeScreenPreview() {
  val point = PickupPoint(LatLng(37.5571, 126.942), LocationDescription("홍대입구역 앞", "양화로 · 도로 북쪽 인도"), null)
  val records =
    listOf(
      PickupRecord("p1", Contact("c1", "이서연", "01000001234"), null, point, true, 0, 20, 0L),
      PickupRecord("p2", Contact("c2", "박지호", "01000009012"), null, point.copy(description = LocationDescription("굿타임호텔 앞", "")), true, 1, 45, 0L),
    )
  WayTheme { HomeScreen(state = HomeUiState(recent = records), onCreatePickup = {}, onReuse = {}) }
}
