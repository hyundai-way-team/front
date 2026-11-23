package com.kscho.way.di

import android.content.Context
import android.util.Log
import com.kscho.way.contact.DeviceContacts
import com.kscho.way.data.history.FilePickupHistory
import com.kscho.way.data.mock.MockWayApi
import com.kscho.way.data.mock.MockWorld
import com.kscho.way.data.place.OfflinePlaceRepository
import com.kscho.way.data.place.PlaceIndexReader
import com.kscho.way.data.remote.HttpWayApi
import com.kscho.way.data.remote.WayApi
import com.kscho.way.data.repository.ContactRepositoryImpl
import com.kscho.way.data.repository.PickupRepositoryImpl
import com.kscho.way.data.repository.StopRestrictionRepositoryImpl
import com.kscho.way.domain.repository.contact.ContactRepository
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import com.kscho.way.domain.repository.pickup.PickupRepository
import com.kscho.way.domain.repository.place.PlaceRepository
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import com.kscho.way.domain.repository.vehicle.VehicleProfileRepository
import com.kscho.way.ui.plan.PickupDraft
import com.kscho.way.vehicle.CarLocationTracker
import com.kscho.way.vehicle.LocalVehicleProfile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
  private const val HISTORY_FILE = "pickup_history.json"
  private const val RECENT_PASSENGERS = 20

  @Provides
  @Singleton
  fun provideWayApi(@ApplicationScope scope: CoroutineScope): WayApi =
    WayConfig.SERVER_URL?.let { HttpWayApi(it) }
      ?: MockWayApi(scope, System::currentTimeMillis, log = { Log.i("WayMock", it) })

  @Provides
  @Singleton
  fun provideDeviceContacts(@ApplicationContext context: Context): DeviceContacts = DeviceContacts(context)

  @Provides
  @Singleton
  fun provideOfflinePlaceRepository(
    @ApplicationContext context: Context,
    draft: PickupDraft,
    carLocation: CarLocationTracker,
  ): OfflinePlaceRepository =
    OfflinePlaceRepository(
      open = { context.assets.open(PlaceIndexReader.ASSET_PATH) },
      origin = { draft.check.value?.point?.location ?: draft.place.value?.location ?: carLocation.last },
    )

  @Provides
  fun providePlaceRepository(offlinePlaces: OfflinePlaceRepository): PlaceRepository = offlinePlaces

  @Provides
  @Singleton
  fun provideVehicleProfileRepository(@ApplicationContext context: Context): VehicleProfileRepository =
    LocalVehicleProfile(context)

  @Provides
  @Singleton
  fun providePickupHistoryRepository(@ApplicationContext context: Context): PickupHistoryRepository =
    FilePickupHistory(
      File(context.filesDir, HISTORY_FILE),
      seed = { if (WayConfig.DEMO_HISTORY) MockWorld.demoHistory(System.currentTimeMillis()) else emptyList() },
    )

  @Provides
  @Singleton
  fun provideContactRepository(deviceContacts: DeviceContacts, history: PickupHistoryRepository): ContactRepository =
    ContactRepositoryImpl(deviceContacts, recent = { history.recent(RECENT_PASSENGERS).map { it.passenger } })

  @Provides
  @Singleton
  fun provideStopRestrictionRepository(api: WayApi): StopRestrictionRepository = StopRestrictionRepositoryImpl(api)

  @Provides
  @Singleton
  fun providePickupRepository(api: WayApi): PickupRepository = PickupRepositoryImpl(api)
}
