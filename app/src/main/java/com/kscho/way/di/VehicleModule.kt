package com.kscho.way.di

import android.content.Context
import com.kscho.way.data.device.launcher.ScreenLauncher
import com.kscho.way.data.device.messaging.PleosMessenger
import com.kscho.way.data.device.vehicle.PleosBoardingSensor
import com.kscho.way.data.device.vehicle.PleosNavigator
import com.kscho.way.data.fake.SimulatedNavigator
import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.gateway.Clock
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.gateway.PassengerMessenger
import com.kscho.way.pickup.PickupScreenLauncher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleModule {
  @Binds
  @Singleton
  abstract fun bindScreenLauncher(impl: PickupScreenLauncher): ScreenLauncher

  companion object {
    @Provides
    @Singleton
    fun provideNavigator(
      @ApplicationContext context: Context,
      @ApplicationScope scope: CoroutineScope,
      clock: Clock,
      screenLauncher: ScreenLauncher,
    ): Navigator {
      val real = if (WayConfig.SIMULATED_DRIVE) SimulatedNavigator(scope, clock::now) else PleosNavigator(context, clock::now, screenLauncher)
      return DebugNavigator.wrap(real, scope, clock::now)
    }

    @Provides
    @Singleton
    fun provideBoardingSensor(@ApplicationContext context: Context, clock: Clock): BoardingSensor =
      PleosBoardingSensor(context, clock::now).also { it.start() }

    @Provides
    @Singleton
    fun providePassengerMessenger(screenLauncher: ScreenLauncher): PassengerMessenger =
      PleosMessenger(screenLauncher, openComposer = WayConfig.TEXTS_VIA_PLEOS)
  }
}
