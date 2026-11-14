package com.kscho.way.di

import com.kscho.way.domain.vehicle.Navigator
import kotlinx.coroutines.CoroutineScope

/** 릴리스 빌드: 시연 주행 없이 받은 내비 그대로(디버그 빌드의 같은 이름 파일은 시연 주행을 끼운다). */
internal object DebugNavigator {
  fun wrap(real: Navigator, scope: CoroutineScope, clock: () -> Long): Navigator = real
}
