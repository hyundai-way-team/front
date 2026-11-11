package com.kscho.way.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.repository.StopRestrictionRepository
import com.kscho.way.domain.vehicle.Navigator
import com.kscho.way.theme.WayColors
import com.kscho.way.theme.WayType
import com.kscho.way.ui.common.ActionButton
import com.kscho.way.ui.common.FieldLabel
import com.kscho.way.ui.common.MapFrame
import com.kscho.way.ui.common.Panel
import com.kscho.way.ui.common.ScreenFrame
import com.kscho.way.ui.common.SearchField
import com.kscho.way.ui.common.StepDots
import com.kscho.way.ui.common.BackButton
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchPlaceViewModel(
  private val places: PlaceRepository,
  private val restrictions: StopRestrictionRepository,
  private val navigator: Navigator,
  private val draft: PickupDraft,
) : ViewModel() {
  val query = MutableStateFlow(draft.place.value?.name ?: "")
  val results: StateFlow<List<Place>> =
    query.debounce(250).mapLatest { runCatching { places.search(it) }.getOrDefault(emptyList()) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val selected: StateFlow<Place?> = draft.place
  val here = MutableStateFlow<LatLng?>(null)
  val zones = MutableStateFlow<List<RestrictedZone>>(emptyList())
  val passengerName: String = draft.passenger.value?.callName ?: ""

  fun load() {
    viewModelScope.launch {
      val at = draft.place.value?.location ?: runCatching { navigator.currentLocation() }.getOrNull() ?: return@launch
      here.value = at
      zones.value = runCatching { restrictions.zonesNear(at, 500) }.getOrDefault(emptyList())
    }
  }

  fun select(place: Place) {
    draft.place.value = place
    draft.pinStart.value = place.location
    draft.check.value = null
    viewModelScope.launch { zones.value = runCatching { restrictions.zonesNear(place.location, 400) }.getOrDefault(emptyList()) }
  }
}

/** D-03 약속 장소 검색(와이어프레임 v2): 장소명·주소로 찾아 고른다(기획 3-2). 핀은 다음 화면에서 맞춘다. */
@Composable
fun SearchPlaceScreen(onBack: () -> Unit, onNext: () -> Unit) {
  val c = appContainer()
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  val vm = viewModel { SearchPlaceViewModel(c.places, c.restrictions, c.navigator, c.draft) }
  val query by vm.query.collectAsStateWithLifecycle()
  val results by vm.results.collectAsStateWithLifecycle()
  val selected by vm.selected.collectAsStateWithLifecycle()
  val here by vm.here.collectAsStateWithLifecycle()
  val zones by vm.zones.collectAsStateWithLifecycle()
  val list = rememberLazyListState()
  LaunchedEffect(Unit) { vm.load() }

  ScreenFrame {
    Panel(width = 560.dp) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.fillMaxWidth()) {
          BackButton(onBack)
          Box(Modifier.align(Alignment.CenterEnd)) { StepDots(step = 2, lineWidth = 40.dp) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
          Text("${vm.passengerName} 님과 어디서 만나기로 했나요?", style = WayType.HeadingM, color = Color.Black)
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldLabel("약속 장소")
            SearchField(value = query, onValueChange = { vm.query.value = it }, placeholder = "장소명 또는 주소 입력")
          }
          Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              items(results, key = { it.id }) { place ->
                PlaceItem(place, on = place.id == selected?.id) {
                  keyboard?.hide()
                  focus.clearFocus()
                  vm.select(place)
                }
              }
              if (query.isNotBlank() && results.isEmpty()) {
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
      ActionButton("다음", { keyboard?.hide(); onNext() }, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = selected != null)
    }
    MapFrame {
      val place = selected
      val center = place?.location ?: here
      if (center != null) {
        WayMap(
          center = center,
          metersPerDp = if (place != null) 0.35f else 1.2f,
          modifier = Modifier.fillMaxSize(),
          zones = zones,
          pins = if (place != null) listOf(MapPin(place.location, PinKind.PLACE, "검색한 약속 장소")) else listOf(MapPin(center, PinKind.CAR, "현재 위치")),
          caption = if (place != null) "지도 · 검색한 약속 장소" else "지도 · 현재 위치",
          interactive = true,
        )
      } else {
        Box(Modifier.fillMaxSize().background(WayColors.MapBg))
      }
    }
  }
}

/** 검색 결과 한 줄(Title/M · 주소 M/Regular). 고르면 Primary-50 바탕 · Primary-100 테두리. */
@Composable
private fun PlaceItem(place: Place, on: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Column(
    Modifier.fillMaxWidth()
      .clip(shape)
      .then(if (on) Modifier.background(WayColors.Primary50).border(1.dp, WayColors.Primary100, shape) else Modifier)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(place.name, style = WayType.TitleM, color = WayColors.Grey900)
    Text(place.address, style = WayType.MRegular, color = WayColors.Grey500)
  }
}
