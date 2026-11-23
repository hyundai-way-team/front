package com.kscho.way.di

import android.content.Context
import com.kscho.way.data.mock.SimulatedNavigator
import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.gateway.PassengerMessenger
import com.kscho.way.pickup.PleosMessenger
import com.kscho.way.vehicle.PleosBoardingSensor
import com.kscho.way.vehicle.PleosNavigator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object VehicleModule {
  @Provides
  @Singleton
  fun provideNavigator(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): Navigator {
    val clock: () -> Long = System::currentTimeMillis
    val real = if (WayConfig.SIMULATED_DRIVE) SimulatedNavigator(scope, clock) else PleosNavigator(context, clock)
    return DebugNavigator.wrap(real, scope, clock)
  }

  @Provides
  @Singleton
  fun provideBoardingSensor(@ApplicationContext context: Context): BoardingSensor =
    PleosBoardingSensor(context, System::currentTimeMillis).also { it.start() }

  @Provides
  @Singleton
  fun providePassengerMessenger(@ApplicationContext context: Context): PassengerMessenger = PleosMessenger(context)
}
