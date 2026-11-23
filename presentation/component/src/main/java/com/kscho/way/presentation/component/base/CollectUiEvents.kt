package com.kscho.way.presentation.component.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

@Composable
fun <EVENT : UiEvent> CollectUiEvents(events: Flow<EVENT>, onEvent: (EVENT) -> Unit) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val handler by rememberUpdatedState(onEvent)
  LaunchedEffect(events, lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { events.collect { handler(it) } }
  }
}
