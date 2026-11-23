package com.kscho.way.di

import android.util.Log
import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.gateway.PassengerMessenger
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import com.kscho.way.domain.repository.pickup.PickupRepository
import com.kscho.way.domain.repository.place.PlaceRepository
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import com.kscho.way.domain.usecase.pickup.CheckPickupPointUseCase
import com.kscho.way.ui.plan.PickupDraft
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
  fun providePickupDraft(): PickupDraft = PickupDraft()

  @Provides
  @Singleton
  fun provideCheckPickupPoint(
    places: PlaceRepository,
    restrictions: StopRestrictionRepository,
    navigator: Navigator,
  ): CheckPickupPointUseCase = CheckPickupPointUseCase(places, restrictions, navigator, System::currentTimeMillis)

  @Provides
  @Singleton
  fun providePickupCoordinator(
    pickups: PickupRepository,
    history: PickupHistoryRepository,
    navigator: Navigator,
    boardingSensor: BoardingSensor,
    messenger: PassengerMessenger,
    @ApplicationScope scope: CoroutineScope,
  ): PickupCoordinator =
    PickupCoordinator(
      pickups,
      history,
      navigator,
      boardingSensor,
      messenger,
      scope,
      System::currentTimeMillis,
      onError = { what, e -> Log.w("WayPickup", "$what 실패", e) },
    )
}
