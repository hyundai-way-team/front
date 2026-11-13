package com.kscho.way.ui.plan

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.theme.WayColors
import com.kscho.way.theme.WayType
import com.kscho.way.ui.common.ActionButton
import com.kscho.way.ui.common.BackButton
import com.kscho.way.ui.common.FieldLabel
import com.kscho.way.ui.common.MapFrame
import com.kscho.way.ui.common.Panel
import com.kscho.way.ui.common.ScreenFrame
import com.kscho.way.ui.common.SearchField
import com.kscho.way.ui.common.StepDots
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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SetDestinationViewModel(private val places: PlaceRepository, private val draft: PickupDraft) : ViewModel() {
  val query = MutableStateFlow(draft.destination.value?.name ?: "")
  val results: StateFlow<List<Place>> =
    query.debounce(250).mapLatest { runCatching { places.search(it) }.getOrDefault(emptyList()) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val selected: StateFlow<Place?> = draft.destination
  /** 지도에 보일 곳 — 고른 목적지, 없으면 확정한 픽업 지점 */
  val pickup = draft.check.value?.point?.location

  fun select(place: Place) {
    draft.destination.value = place
  }

  /** 검색어를 다 지우면 목적지도 비운다(목적지 없이 출발). */
  fun edit(text: String) {
    query.value = text
    if (text.isBlank()) draft.destination.value = null
  }
}

/** D-05 픽업 후 목적지 설정(와이어프레임 v2) — 정하지 않아도 '다음'으로 넘어간다. 정하면 탑승 뒤 그곳으로 안내한다. */
@Composable
fun SetDestinationScreen(onBack: () -> Unit, onNext: () -> Unit) {
  val c = appContainer()
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  val vm = viewModel { SetDestinationViewModel(c.places, c.draft) }
  val query by vm.query.collectAsStateWithLifecycle()
  val results by vm.results.collectAsStateWithLifecycle()
  val selected by vm.selected.collectAsStateWithLifecycle()
  val list = rememberLazyListState()

  ScreenFrame {
    Panel(width = 560.dp) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.fillMaxWidth()) {
          BackButton(onBack)
          Box(Modifier.align(Alignment.CenterEnd)) { StepDots(step = 3, lineWidth = 40.dp) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("픽업 후 목적지를 설정해 주세요", style = WayType.HeadingM, color = Color.Black)
            Text("목적지를 설정하지 않아도 출발할 수 있어요.", style = WayType.LMedium, color = WayColors.Grey500)
          }
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldLabel("목적지")
            SearchField(value = query, onValueChange = vm::edit, placeholder = "장소명 또는 주소 입력")
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
              if (query.isNotBlank() && selected == null && results.isEmpty()) {
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
      ActionButton("다음", { keyboard?.hide(); onNext() }, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
    MapFrame {
      val dest = selected
      val center = dest?.location ?: vm.pickup
      if (center != null) {
        WayMap(
          center = center,
          metersPerDp = if (dest != null) 0.35f else 1.2f,
          modifier = Modifier.fillMaxSize(),
          pins = if (dest != null) listOf(MapPin(dest.location, PinKind.PLACE, "목적지")) else listOf(MapPin(center, PinKind.PICKUP, "픽업 위치")),
          caption = if (dest != null) "지도 · 픽업 후 목적지" else "지도 · 픽업 위치",
          interactive = true,
        )
      } else {
        Box(Modifier.fillMaxSize().background(WayColors.MapBg))
      }
    }
  }
}
