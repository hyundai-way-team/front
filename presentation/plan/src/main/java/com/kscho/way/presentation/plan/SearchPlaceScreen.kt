package com.kscho.way.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.Place
import com.kscho.way.presentation.component.base.CollectUiEvents
import com.kscho.way.presentation.component.common.ActionButton
import com.kscho.way.presentation.component.common.BackButton
import com.kscho.way.presentation.component.common.FieldLabel
import com.kscho.way.presentation.component.common.MapFrame
import com.kscho.way.presentation.component.common.Panel
import com.kscho.way.presentation.component.common.ScreenFrame
import com.kscho.way.presentation.component.common.SearchField
import com.kscho.way.presentation.component.common.StepDots
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayTheme
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.map.MapPin
import com.kscho.way.presentation.map.PinKind
import com.kscho.way.presentation.map.WayMap

@Composable
fun SearchPlaceRoute(
  onBack: () -> Unit,
  onNavigateToAdjustPin: () -> Unit,
  viewModel: SearchPlaceViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { viewModel.load() }
  CollectUiEvents(viewModel.uiEvent) { event ->
    when (event) {
      SearchPlaceEvent.NavigateToAdjustPin -> onNavigateToAdjustPin()
    }
  }
  SearchPlaceScreen(
    state = state,
    onBack = onBack,
    onQueryChange = viewModel::changeQuery,
    onSelect = viewModel::select,
    onNext = viewModel::next,
  )
}

@Composable
fun SearchPlaceScreen(
  state: SearchPlaceUiState,
  onBack: () -> Unit,
  onQueryChange: (String) -> Unit,
  onSelect: (Place) -> Unit,
  onNext: () -> Unit,
) {
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  val list = rememberLazyListState()

  ScreenFrame {
    Panel(width = 560.dp) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.fillMaxWidth()) {
          BackButton(onBack)
          Box(Modifier.align(Alignment.CenterEnd)) { StepDots(step = 2, lineWidth = 40.dp) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
          Text("${state.passengerName} 님과 어디서 만나기로 했나요?", style = WayType.HeadingM, color = Color.Black)
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldLabel("약속 장소")
            SearchField(value = state.query, onValueChange = onQueryChange, placeholder = "장소명 또는 주소 입력")
          }
          Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              items(state.results, key = { it.id }) { place ->
                PlaceItem(place, on = place.id == state.selected?.id) {
                  keyboard?.hide()
                  focus.clearFocus()
                  onSelect(place)
                }
              }
              if (state.query.isNotBlank() && state.results.isEmpty()) {
                item { Text("검색 결과가 없어요", style = WayType.MRegular, color = WayColors.Grey500, modifier = Modifier.padding(16.dp)) }
              }
            }
            if (list.canScrollForward) Box(
              Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White)))
            )
          }
        }
      }
      ActionButton("다음", { keyboard?.hide(); onNext() }, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = state.selected != null)
    }
    MapFrame {
      val place = state.selected
      val center = place?.location ?: state.here
      if (center != null) {
        WayMap(
          center = center,
          metersPerDp = if (place != null) 0.35f else 1.2f,
          modifier = Modifier.fillMaxSize(),
          zones = state.zones,
          pins = if (place != null) listOf(MapPin(place.location, PinKind.PLACE, "검색한 장소")) else listOf(MapPin(center, PinKind.CAR, "현재 위치")),
          caption = if (place != null) "지도 · 검색한 약속 장소" else "지도 · 현재 위치",
          interactive = true,
        )
      } else {
        Box(Modifier.fillMaxSize().background(WayColors.MapBg))
      }
    }
  }
}


@Preview(widthDp = 1280, heightDp = 720)
@Composable
internal fun SearchPlaceScreenPreview() {
  val places =
    listOf(
      Place("p1", "홍대입구역", "서울 마포구 양화로 160", LatLng(37.5571, 126.9245)),
      Place("p2", "굿타임호텔", "서울 마포구 와우산로 21", LatLng(37.5555, 126.9302)),
    )
  WayTheme {
    SearchPlaceScreen(
      state = SearchPlaceUiState(passengerName = "서연", query = "홍대", results = places, selected = places.first()),
      onBack = {},
      onQueryChange = {},
      onSelect = {},
      onNext = {},
    )
  }
}
