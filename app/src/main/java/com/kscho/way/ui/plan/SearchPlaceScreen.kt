package com.kscho.way.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.domain.model.MapFeatures
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.theme.WayColors
import com.kscho.way.ui.common.PrimaryButton
import com.kscho.way.ui.common.StepHeader
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.common.card
import com.kscho.way.ui.map.MapLabel
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchPlaceViewModel(private val places: PlaceRepository, private val draft: PickupDraft) : ViewModel() {
  val query = MutableStateFlow(draft.place.value?.name ?: "")
  val results: StateFlow<List<Place>> =
    query.debounce(250).mapLatest { runCatching { places.search(it) }.getOrDefault(emptyList()) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val selected: StateFlow<Place?> = draft.place
  val features: StateFlow<MapFeatures?> =
    draft.place.filterNotNull().mapLatest { runCatching { places.mapFeatures(it.location, 400) }.getOrNull() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
  val passengerName: String = draft.passenger.value?.callName ?: ""

  fun select(place: Place) {
    draft.place.value = place
    draft.pinStart.value = place.location
    draft.check.value = null
  }
}

/** D-03 약속 장소 검색: 장소명·주소로 찾고 지도에 핀을 놓는다(기획 3-2). */
@Composable
fun SearchPlaceScreen(onBack: () -> Unit, onNext: () -> Unit) {
  val c = appContainer()
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  val vm = viewModel { SearchPlaceViewModel(c.places, c.draft) }
  val query by vm.query.collectAsStateWithLifecycle()
  val results by vm.results.collectAsStateWithLifecycle()
  val selected by vm.selected.collectAsStateWithLifecycle()
  val features by vm.features.collectAsStateWithLifecycle()

  Row(Modifier.fillMaxSize().background(Color.White)) {
    Column(Modifier.width(560.dp).fillMaxHeight().padding(40.dp)) {
      StepHeader(step = 2, onBack = onBack)
      Spacer(Modifier.height(20.dp))
      Text("${vm.passengerName} 님과 어디서 만나기로 했나요?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
      Spacer(Modifier.height(20.dp))
      Text("약속 장소 (장소명 또는 주소)", fontSize = 15.sp, color = WayColors.Sub)
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = query,
        onValueChange = { vm.query.value = it },
        placeholder = { Text("예) 신촌 XX 빌라 · 신촌로 12길", fontSize = 18.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 18.sp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(16.dp))
      LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(results, key = { it.id }) { place ->
          val on = place.id == selected?.id
          Column(Modifier.fillMaxWidth().card(on).clickable { keyboard?.hide(); focus.clearFocus(); vm.select(place) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(place.name, fontSize = 20.sp, color = WayColors.Ink)
            Text(place.address, fontSize = 15.sp, color = WayColors.Sub)
          }
        }
        if (query.isNotBlank() && results.isEmpty()) {
          item { Text("검색 결과가 없어요", fontSize = 16.sp, color = WayColors.Sub, modifier = Modifier.padding(8.dp)) }
        }
      }
      PrimaryButton("다음: 핀을 정차 지점에 맞추기", onNext, Modifier.fillMaxWidth(), enabled = selected != null)
    }
    val place = selected
    if (place != null) {
      Box(Modifier.weight(1f).fillMaxHeight()) {
        WayMap(
          center = place.location,
          metersPerDp = 0.35f,
          modifier = Modifier.fillMaxSize(),
          features = features,
          pins = listOf(MapPin(place.location, PinKind.PLACE, "검색한 약속 장소")),
          caption = "지도 · 검색한 곳으로 이동하고 핀이 놓여요",
          interactive = true,
        )
        MapLabel("주소 그대로면 골목 안일 수 있어요. 다음 단계에서 차를 세울 곳으로 옮겨요", Modifier.align(Alignment.BottomStart).padding(20.dp))
      }
    } else {
      Box(Modifier.weight(1f).fillMaxHeight().background(WayColors.MapBg), contentAlignment = Alignment.Center) {
        Text("장소를 고르면 지도에 핀이 놓여요", fontSize = 18.sp, color = WayColors.Sub)
      }
    }
  }
}
