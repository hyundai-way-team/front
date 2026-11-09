package com.kscho.way.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.MapFeatures
import com.kscho.way.domain.model.PickupRecord
import com.kscho.way.domain.repository.PickupHistoryRepository
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.vehicle.Navigator
import com.kscho.way.theme.WayColors
import com.kscho.way.ui.common.Avatar
import com.kscho.way.ui.common.Format
import com.kscho.way.ui.common.PrimaryButton
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.common.card
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(val recent: List<PickupRecord> = emptyList(), val here: LatLng? = null, val features: MapFeatures? = null)

class HomeViewModel(private val history: PickupHistoryRepository, private val navigator: Navigator, private val places: PlaceRepository) :
  ViewModel() {
  private val _state = MutableStateFlow(HomeUiState())
  val state = _state.asStateFlow()

  fun refresh() {
    viewModelScope.launch {
      runCatching { history.recent(limit = 3) }.onSuccess { list -> _state.update { it.copy(recent = list) } }
      val here = runCatching { navigator.currentLocation() }.getOrNull() ?: return@launch
      _state.update { it.copy(here = here) }
      runCatching { places.mapFeatures(here, 600) }.onSuccess { f -> _state.update { it.copy(features = f) } }
    }
  }
}

/** D-01 홈: 픽업 만들기 · 최근 픽업 · 현재 위치 지도. */
@Composable
fun HomeScreen(onCreatePickup: () -> Unit, onReuse: (PickupRecord) -> Unit) {
  val c = appContainer()
  val vm = viewModel { HomeViewModel(c.history, c.navigator, c.places) }
  val state by vm.state.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { vm.refresh() }

  Row(Modifier.fillMaxSize().background(Color.White)) {
    Column(Modifier.width(480.dp).fillMaxHeight().padding(40.dp)) {
      Text("WAY - Where Are You?", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
      Text("정확한 픽업 위치를 정하고 출발하세요", fontSize = 18.sp, color = WayColors.Sub)
      Spacer(Modifier.height(24.dp))
      PrimaryButton("＋  픽업 만들기", onCreatePickup, Modifier.fillMaxWidth().height(88.dp))
      Spacer(Modifier.height(24.dp))
      Text("최근 픽업", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = WayColors.Sub)
      Spacer(Modifier.height(12.dp))
      if (state.recent.isEmpty()) {
        Text("아직 픽업 기록이 없어요", fontSize = 16.sp, color = WayColors.Sub)
      }
      LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.recent, key = { it.id }) { record -> RecentPickupRow(record) { onReuse(record) } }
      }
    }
    val here = state.here
    if (here != null) {
      WayMap(
        center = here,
        metersPerDp = 1.2f,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        features = state.features,
        pins = listOf(MapPin(here, PinKind.CAR, "현재 위치")),
        caption = "지도 · 현재 위치",
        interactive = true,
      )
    } else {
      Spacer(Modifier.weight(1f).fillMaxHeight().background(WayColors.MapBg))
    }
  }
}

@Composable
private fun RecentPickupRow(record: PickupRecord, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Avatar()
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(record.passenger.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = WayColors.Ink)
      Text(record.pickupPoint.description.title, fontSize = 15.sp, color = WayColors.Sub)
    }
    Text(Format.ago(record.endedAtMillis, System.currentTimeMillis()), fontSize = 14.sp, color = WayColors.Sub)
  }
}
