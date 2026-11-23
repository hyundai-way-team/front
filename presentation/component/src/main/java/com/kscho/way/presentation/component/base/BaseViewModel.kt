package com.kscho.way.presentation.component.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<STATE : UiState, EVENT : UiEvent>(initialState: STATE) : ViewModel() {
  private val _uiState = MutableStateFlow(initialState)
  val uiState: StateFlow<STATE> = _uiState.asStateFlow()

  private val _uiEvent = Channel<EVENT>(Channel.BUFFERED)
  val uiEvent: Flow<EVENT> = _uiEvent.receiveAsFlow()

  protected val currentState: STATE
    get() = _uiState.value

  protected fun updateState(reducer: STATE.() -> STATE) {
    _uiState.update { it.reducer() }
  }

  protected fun emitEvent(event: EVENT) {
    viewModelScope.launch { _uiEvent.send(event) }
  }
}
