package com.kscho.way.presentation.plan

import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.pickup.PickupDraft
import com.kscho.way.domain.usecase.contact.SearchContactsUseCase
import com.kscho.way.presentation.component.base.BaseViewModel
import com.kscho.way.presentation.component.base.UiEvent
import com.kscho.way.presentation.component.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ChoosePassengerUiState(
  val query: String = "",
  val contacts: List<Contact>? = null,
  val suggestions: List<Contact> = emptyList(),
  val selected: Contact? = null,
) : UiState

sealed interface ChoosePassengerEvent : UiEvent {
  data object NavigateToSearchPlace : ChoosePassengerEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChoosePassengerViewModel @Inject constructor(
  private val searchContacts: SearchContactsUseCase,
  private val draft: PickupDraft,
) : BaseViewModel<ChoosePassengerUiState, ChoosePassengerEvent>(ChoosePassengerUiState()) {
  private val query = MutableStateFlow("")

  init {
    query
      .debounce(SEARCH_DEBOUNCE_MILLIS)
      .mapLatest { q -> if (q.isBlank()) emptyList() else runCatching { searchContacts(q) }.getOrDefault(emptyList()) }
      .onEach { suggestions -> updateState { copy(suggestions = suggestions) } }
      .launchIn(viewModelScope)
    draft.passenger.onEach { selected -> updateState { copy(selected = selected) } }.launchIn(viewModelScope)
  }

  fun load() {
    viewModelScope.launch {
      val contacts = runCatching { searchContacts("") }.getOrDefault(emptyList())
      updateState { copy(contacts = contacts) }
    }
  }

  fun changeQuery(value: String) {
    query.value = value
    updateState { copy(query = value) }
  }

  fun select(contact: Contact) {
    if (draft.passenger.value?.id != contact.id) {
      draft.reset()
      draft.passenger.value = contact
    }
  }

  fun selectAndContinue(contact: Contact) {
    select(contact)
    next()
  }

  fun next() {
    emitEvent(ChoosePassengerEvent.NavigateToSearchPlace)
  }

  private companion object {
    const val SEARCH_DEBOUNCE_MILLIS = 150L
  }
}
