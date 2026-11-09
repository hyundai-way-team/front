package com.kscho.way.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.repository.ContactRepository
import com.kscho.way.theme.WayColors
import com.kscho.way.ui.common.Avatar
import com.kscho.way.ui.common.PrimaryButton
import com.kscho.way.ui.common.StepHeader
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.common.card
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ChoosePassengerViewModel(private val contacts: ContactRepository, private val draft: PickupDraft) : ViewModel() {
  val query = MutableStateFlow("")
  val results: StateFlow<List<Contact>> =
    query.debounce(200).mapLatest { runCatching { contacts.search(it) }.getOrDefault(emptyList()) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val selected: StateFlow<Contact?> = draft.passenger

  fun select(contact: Contact) {
    if (draft.passenger.value?.id != contact.id) {
      // 다른 사람을 고르면 장소부터 다시
      draft.reset()
      draft.passenger.value = contact
    }
  }
}

/** D-02 동승자 선택: 연락처에서 1명(기획 3-1). */
@Composable
fun ChoosePassengerScreen(onBack: () -> Unit, onNext: () -> Unit) {
  val c = appContainer()
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  val vm = viewModel { ChoosePassengerViewModel(c.contacts, c.draft) }
  val query by vm.query.collectAsStateWithLifecycle()
  val results by vm.results.collectAsStateWithLifecycle()
  val selected by vm.selected.collectAsStateWithLifecycle()

  Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal = 48.dp, vertical = 32.dp)) {
    StepHeader(step = 1, onBack = onBack, title = "누구를 태우러 가나요?")
    Spacer(Modifier.height(24.dp))
    Text("Pleos 연락처에서 검색", fontSize = 15.sp, color = WayColors.Sub)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = query,
      onValueChange = { vm.query.value = it },
      placeholder = { Text("이름 또는 전화번호", fontSize = 18.sp) },
      singleLine = true,
      textStyle = TextStyle(fontSize = 18.sp),
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      items(results, key = { it.id }) { contact ->
        val on = contact.id == selected?.id
        Row(
          Modifier.fillMaxWidth().card(on).clickable { keyboard?.hide(); focus.clearFocus(); vm.select(contact) }.padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = on, onClick = { keyboard?.hide(); focus.clearFocus(); vm.select(contact) })
          Spacer(Modifier.width(8.dp))
          Avatar(on)
          Spacer(Modifier.width(16.dp))
          Text(contact.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = WayColors.Ink, modifier = Modifier.weight(1f))
          Text(contact.maskedPhone, fontSize = 16.sp, color = WayColors.Sub)
        }
      }
      if (results.isEmpty()) {
        item { Text("찾는 연락처가 없어요", fontSize = 16.sp, color = WayColors.Sub, modifier = Modifier.padding(8.dp)) }
      }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
      PrimaryButton("다음", onNext, Modifier.width(280.dp), enabled = selected != null)
    }
  }
}
