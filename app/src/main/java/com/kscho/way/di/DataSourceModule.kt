package com.kscho.way.di

import android.content.Context
import com.kscho.way.contact.DeviceContacts
import com.kscho.way.data.dataSource.local.contact.ContactLocalDataSource
import com.kscho.way.data.dataSource.local.history.PickupHistoryFileDataSource
import com.kscho.way.data.dataSource.local.history.PickupHistoryLocalDataSource
import com.kscho.way.data.dataSource.remote.PickupRemoteDataSource
import com.kscho.way.data.dataSource.remote.PickupRemoteDataSourceImpl
import com.kscho.way.data.dataSource.remote.StopZoneRemoteDataSource
import com.kscho.way.data.dataSource.remote.StopZoneRemoteDataSourceImpl
import com.kscho.way.data.fake.FakeWorld
import com.kscho.way.domain.gateway.Clock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {
  @Binds
  @Singleton
  abstract fun bindStopZoneRemoteDataSource(impl: StopZoneRemoteDataSourceImpl): StopZoneRemoteDataSource

  @Binds
  @Singleton
  abstract fun bindPickupRemoteDataSource(impl: PickupRemoteDataSourceImpl): PickupRemoteDataSource

  @Binds
  @Singleton
  abstract fun bindContactLocalDataSource(impl: DeviceContacts): ContactLocalDataSource

  companion object {
    private const val HISTORY_FILE = "pickup_history.json"

    @Provides
    @Singleton
    fun providePickupHistoryLocalDataSource(@ApplicationContext context: Context, clock: Clock): PickupHistoryLocalDataSource =
      PickupHistoryFileDataSource(
        File(context.filesDir, HISTORY_FILE),
        seed = { if (WayConfig.DEMO_HISTORY) FakeWorld.demoHistory(clock.now()) else emptyList() },
      )
  }
}
