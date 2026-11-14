package com.kscho.way.di

import com.kscho.way.debug.DemoDriveNavigator
import com.kscho.way.domain.vehicle.Navigator
import kotlinx.coroutines.CoroutineScope

/** 디버그 빌드: adb 로 시연 경로를 넣으면 그 길을 달리는 시연 주행([DemoDriveNavigator]). 넣지 않으면 받은 내비 그대로 동작한다. */
internal object DebugNavigator {
  fun wrap(real: Navigator, scope: CoroutineScope, clock: () -> Long): Navigator = DemoDriveNavigator(real, scope, clock)
}
