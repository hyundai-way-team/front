package com.kscho.way.presentation.plan

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.presentation.component.base.CollectUiEvents
import com.kscho.way.presentation.component.common.ActionButton
import com.kscho.way.presentation.component.common.FieldLabel
import com.kscho.way.presentation.component.common.Panel
import com.kscho.way.presentation.component.common.ScreenFrame
import com.kscho.way.presentation.component.common.SearchField
import com.kscho.way.presentation.component.common.StepHeader
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayTheme
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.plan.R

@Composable
fun ChoosePassengerRoute(
  onBack: () -> Unit,
  onNavigateToSearchPlace: () -> Unit,
  viewModel: ChoosePassengerViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var contactsAllowed by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
  }
  val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    contactsAllowed = granted
    viewModel.load()
  }
  LaunchedEffect(Unit) {
    viewModel.load()
    if (!contactsAllowed) askContacts.launch(Manifest.permission.READ_CONTACTS)
  }
  CollectUiEvents(viewModel.uiEvent) { event ->
    when (event) {
      ChoosePassengerEvent.NavigateToSearchPlace -> onNavigateToSearchPlace()
    }
  }
  ChoosePassengerScreen(
    state = state,
    contactsAllowed = contactsAllowed,
    onBack = onBack,
    onQueryChange = viewModel::changeQuery,
    onSelect = viewModel::select,
    onSuggestionClick = viewModel::selectAndContinue,
    onNext = viewModel::next,
  )
}

@Composable
fun ChoosePassengerScreen(
  state: ChoosePassengerUiState,
  contactsAllowed: Boolean,
  onBack: () -> Unit,
  onQueryChange: (String) -> Unit,
  onSelect: (Contact) -> Unit,
  onSuggestionClick: (Contact) -> Unit,
  onNext: () -> Unit,
) {
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  val density = LocalDensity.current
  val contacts = state.contacts.orEmpty()
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
                value = state.query,
                onValueChange = onQueryChange,
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
                    items(contacts, key = { it.id }) { contact ->
                      ContactCard(contact, on = contact.id == state.selected?.id) {
                        done()
                        onSelect(contact)
                      }
                    }
                  }
                  if (state.contacts != null && contacts.isEmpty()) {
                    Text(
                      if (contactsAllowed) "연락처가 없어요. 차에 휴대폰을 연결하면 연락처를 불러와요." else "연락처를 보려면 연락처 접근을 허용해 주세요.",
                      style = WayType.LMedium,
                      color = WayColors.Grey500,
                      modifier = Modifier.padding(top = 8.dp),
                    )
                  }
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
        if (state.suggestions.isNotEmpty() && state.query.isNotBlank()) {
          Column(
            Modifier.offset(y = with(density) { (fieldBottomPx - boxTopPx).toDp() } + 12.dp)
              .fillMaxWidth()
              .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = WayColors.ShadowMedium, spotColor = WayColors.ShadowMedium)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White)
              .border(1.dp, WayColors.Grey100, RoundedCornerShape(16.dp))
          ) {
            state.suggestions.take(MAX_SUGGESTIONS).forEach { contact ->
              Row(
                Modifier.fillMaxWidth()
                  .clickable(role = Role.Button) {
                    done()
                    onSuggestionClick(contact)
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
        ActionButton("다음", { done(); onNext() }, Modifier.width(277.dp), enabled = state.selected != null)
      }
    }
  }
}

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

@Preview(widthDp = 1280, heightDp = 720)
@Composable
internal fun ChoosePassengerScreenPreview() {
  val contacts = listOf(Contact("c1", "이서연", "01000001234"), Contact("c2", "박지호", "01000009012"), Contact("c3", "김민준", "01000005678"))
  WayTheme {
    ChoosePassengerScreen(
      state = ChoosePassengerUiState(contacts = contacts, selected = contacts.first()),
      contactsAllowed = true,
      onBack = {},
      onQueryChange = {},
      onSelect = {},
      onSuggestionClick = {},
      onNext = {},
    )
  }
}

private const val MAX_SUGGESTIONS = 5
