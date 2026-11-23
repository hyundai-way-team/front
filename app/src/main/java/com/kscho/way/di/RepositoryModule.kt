package com.kscho.way.di

import android.content.Context
import com.kscho.way.data.dataSource.local.place.PlaceIndexReader
import com.kscho.way.data.device.repository.VehicleProfileRepositoryImpl
import com.kscho.way.data.repository.contact.ContactRepositoryImpl
import com.kscho.way.data.repository.history.PickupHistoryRepositoryImpl
import com.kscho.way.data.repository.pickup.PickupRepositoryImpl
import com.kscho.way.data.repository.place.PlaceRepositoryImpl
import com.kscho.way.data.repository.restriction.StopRestrictionRepositoryImpl
import com.kscho.way.domain.repository.contact.ContactRepository
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import com.kscho.way.domain.repository.pickup.PickupRepository
import com.kscho.way.domain.repository.place.PlaceRepository
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import com.kscho.way.domain.repository.vehicle.VehicleProfileRepository
import com.kscho.way.ui.plan.PickupDraft
import com.kscho.way.vehicle.CarLocationTracker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
  @Binds
  @Singleton
  abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

  @Binds
  @Singleton
  abstract fun bindPickupHistoryRepository(impl: PickupHistoryRepositoryImpl): PickupHistoryRepository

  @Binds
  @Singleton
  abstract fun bindStopRestrictionRepository(impl: StopRestrictionRepositoryImpl): StopRestrictionRepository

  @Binds
  @Singleton
  abstract fun bindPickupRepository(impl: PickupRepositoryImpl): PickupRepository

  @Binds
  abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository

  companion object {
    @Provides
    @Singleton
    fun providePlaceRepositoryImpl(
      @ApplicationContext context: Context,
      draft: PickupDraft,
      carLocation: CarLocationTracker,
    ): PlaceRepositoryImpl =
      PlaceRepositoryImpl(
        open = { context.assets.open(PlaceIndexReader.ASSET_PATH) },
        origin = { draft.check.value?.point?.location ?: draft.place.value?.location ?: carLocation.last },
      )

    @Provides
    @Singleton
    fun provideVehicleProfileRepository(@ApplicationContext context: Context): VehicleProfileRepository =
      VehicleProfileRepositoryImpl(context)
  }
}
