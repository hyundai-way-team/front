package com.kscho.way.ui.plan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.R
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.repository.ContactRepository
import com.kscho.way.theme.WayColors
import com.kscho.way.theme.WayType
import com.kscho.way.ui.common.ActionButton
import com.kscho.way.ui.common.FieldLabel
import com.kscho.way.ui.common.Panel
import com.kscho.way.ui.common.ScreenFrame
import com.kscho.way.ui.common.SearchField
import com.kscho.way.ui.common.StepHeader
import com.kscho.way.ui.common.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ChoosePassengerViewModel(private val contacts: ContactRepository, private val draft: PickupDraft) : ViewModel() {
  val query = MutableStateFlow("")

  /** '최근 및 저장된 연락처' — 최근에 태운 사람 먼저, 그다음 기기 연락처. 처음 한 번 불러오고, 연락처 권한을 받은 뒤 다시 부른다. */
  private val _all = MutableStateFlow<List<Contact>?>(null)
  val all: StateFlow<List<Contact>?> = _all.asStateFlow()

  fun load() {
    viewModelScope.launch { _all.value = runCatching { contacts.search("") }.getOrDefault(emptyList()) }
  }

  /** 입력한 이름·번호로 찾은 사람(검색 칸 아래 목록) */
  val suggestions: StateFlow<List<Contact>> =
    query
      .debounce(150)
      .mapLatest { q -> if (q.isBlank()) emptyList() else runCatching { contacts.search(q) }.getOrDefault(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val selected: StateFlow<Contact?> = draft.passenger

  fun select(contact: Contact) {
    if (draft.passenger.value?.id != contact.id) {
      // 다른 사람을 고르면 장소부터 다시
      draft.reset()
      draft.passenger.value = contact
    }
  }
}

/** D-02 동승자 선택(와이어프레임 v2): 연락처에서 1명(기획 3-1). 검색 목록에서 고르면 바로 다음(D-03)으로. */
@Composable
fun ChoosePassengerScreen(onBack: () -> Unit, onNext: () -> Unit) {
  val c = appContainer()
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  val density = LocalDensity.current
  val vm = viewModel { ChoosePassengerViewModel(c.contacts, c.draft) }
  val query by vm.query.collectAsStateWithLifecycle()
  val loaded by vm.all.collectAsStateWithLifecycle()
  val all = loaded.orEmpty()
  // 연락처 = 차에 연결된 휴대폰 연락처(기기 연락처). 권한을 받으면 다시 불러온다
  var contactsAllowed by remember { mutableStateOf(c.deviceContacts.granted) }
  val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    contactsAllowed = ok
    vm.load()
  }
  LaunchedEffect(Unit) {
    vm.load()
    if (!contactsAllowed) askContacts.launch(Manifest.permission.READ_CONTACTS)
  }
  val suggestions by vm.suggestions.collectAsStateWithLifecycle()
  val selected by vm.selected.collectAsStateWithLifecycle()
  // 검색 칸 아래 목록 자리: 칸 아래 끝 − 틀 위 끝(둘 다 창 기준)
  var boxTopPx by remember { mutableIntStateOf(0) }
  var fieldBottomPx by remember { mutableIntStateOf(0) }
  val grid = rememberLazyGridState()

  fun done() {
    keyboard?.hide()
    focus.clearFocus()
  }

  ScreenFrame {
    Panel(width = null) {
      Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { boxTopPx = it.positionInRoot().y.toInt() }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(40.dp)) {
          StepHeader(step = 1, onBack = onBack, title = "누구를 태우러 가나요?", lineWidth = 60.dp)
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              FieldLabel("Pleos 연락처에서 검색")
              SearchField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = "이름 또는 전화번호 입력",
                modifier = Modifier.onGloballyPositioned { fieldBottomPx = (it.positionInRoot().y + it.size.height).toInt() },
              )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              FieldLabel("최근 및 저장된 연락처")
              Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                  LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                  ) {
                    items(all, key = { it.id }) { contact ->
                      ContactCard(contact, on = contact.id == selected?.id) {
                        done()
                        vm.select(contact)
                      }
                    }
                  }
                  if (loaded != null && all.isEmpty()) {
                    Text(
                      if (contactsAllowed) "연락처가 없어요. 차에 휴대폰을 연결하면 연락처를 불러와요." else "연락처를 보려면 연락처 접근을 허용해 주세요.",
                      style = WayType.LMedium,
                      color = WayColors.Grey500,
                      modifier = Modifier.padding(top = 8.dp),
                    )
                  }
                  // 아래로 더 있음을 흐리게(Scroll Fade)
                  if (grid.canScrollForward) Box(
                    Modifier.align(Alignment.BottomCenter)
                      .fillMaxWidth()
                      .height(100.dp)
                      .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White)))
                  )
                }
                ScrollThumb(grid, Modifier.fillMaxHeight())
              }
            }
          }
        }
        // 검색 칸 바로 아래 찾은 사람 목록 — 고르면 바로 다음 화면(D-03)
        if (suggestions.isNotEmpty() && query.isNotBlank()) {
          Column(
            Modifier.offset(y = with(density) { (fieldBottomPx - boxTopPx).toDp() } + 12.dp)
              .fillMaxWidth()
              .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = WayColors.ShadowMedium, spotColor = WayColors.ShadowMedium)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White)
              .border(1.dp, WayColors.Grey100, RoundedCornerShape(16.dp))
          ) {
            suggestions.take(5).forEach { contact ->
              Row(
                Modifier.fillMaxWidth()
                  .clickable(role = Role.Button) {
                    done()
                    vm.select(contact)
                    onNext()
                  }
                  .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(contact.name, style = WayType.TitleM, color = WayColors.Grey900)
                Text(contact.displayPhone, style = WayType.MRegular, color = WayColors.Grey500)
              }
            }
          }
        }
      }
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        ActionButton("다음", { done(); onNext() }, Modifier.width(277.dp), enabled = selected != null)
      }
    }
  }
}

/** 연락처 카드(라디오 · 이름 · 번호). 고르면 Primary-50 바탕 · Primary-500 테두리. */
@Composable
private fun ContactCard(contact: Contact, on: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Row(
    Modifier.fillMaxWidth()
      .clip(shape)
      .background(if (on) WayColors.Primary50 else Color.White)
      .border(1.dp, if (on) WayColors.Primary500 else WayColors.Grey300, shape)
      .clickable(role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painterResource(if (on) R.drawable.ic_radio_on else R.drawable.ic_radio_off),
      contentDescription = if (on) "선택됨" else null,
      tint = Color.Unspecified,
      modifier = Modifier.size(32.dp),
    )
    Column(Modifier.weight(1f)) {
      Text(contact.name, style = WayType.TitleL, color = Color.Black)
      Text(contact.displayPhone, style = WayType.MRegular, color = WayColors.Grey500)
    }
  }
}

/** 목록 옆 스크롤 막대(폭 8 · Grey-200). 넘칠 때만 보인다. */
@Composable
private fun ScrollThumb(state: LazyGridState, modifier: Modifier = Modifier) {
  val info = state.layoutInfo
  val total = info.totalItemsCount
  val visible = info.visibleItemsInfo.size
  if (total == 0 || visible >= total) return
  BoxWithConstraints(modifier.width(8.dp)) {
    val track = maxHeight.value
    val thumb = (track * visible / total).coerceAtLeast(48f)
    val top = (track - thumb) * state.firstVisibleItemIndex / (total - visible).coerceAtLeast(1)
    Box(Modifier.offset(y = top.dp).width(8.dp).height(thumb.dp).clip(CircleShape).background(WayColors.Grey200))
  }
}
