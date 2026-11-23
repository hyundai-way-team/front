package com.kscho.way.di

import com.kscho.way.domain.gateway.Navigator
import kotlinx.coroutines.CoroutineScope

internal object DebugNavigator {
  fun wrap(real: Navigator, scope: CoroutineScope, clock: () -> Long): Navigator = real
}
