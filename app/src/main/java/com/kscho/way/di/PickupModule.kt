package com.kscho.way.di

import android.util.Log
import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.gateway.Clock
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.gateway.PassengerMessenger
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import com.kscho.way.domain.repository.pickup.PickupRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object PickupModule {
  @Provides
  @Singleton
  fun providePickupCoordinator(
    pickups: PickupRepository,
    history: PickupHistoryRepository,
    navigator: Navigator,
    boardingSensor: BoardingSensor,
    messenger: PassengerMessenger,
    @ApplicationScope scope: CoroutineScope,
    clock: Clock,
  ): PickupCoordinator =
    PickupCoordinator(
      pickups,
      history,
      navigator,
      boardingSensor,
      messenger,
      scope,
      clock::now,
      onError = { what, e -> Log.w("WayPickup", "$what 실패", e) },
    )
}
