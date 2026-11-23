package com.kscho.way.di

import android.util.Log
import com.kscho.way.data.api.WayApi
import com.kscho.way.data.api.WayNetwork
import com.kscho.way.data.dataSource.remote.PassengerSocketDataSource
import com.kscho.way.data.dataSource.remote.PassengerSocketDataSourceImpl
import com.kscho.way.data.fake.FakeWayApi
import com.kscho.way.domain.gateway.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
  @Provides
  @Singleton
  fun provideJson(): Json = WayNetwork.json

  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient = WayNetwork.client()

  @Provides
  @Singleton
  fun provideFakeWayApi(@ApplicationScope scope: CoroutineScope, clock: Clock): FakeWayApi =
    FakeWayApi(scope, clock::now, log = { Log.i("WayFake", it) })

  @Provides
  @Singleton
  fun provideWayApi(client: OkHttpClient, json: Json, fakeWayApi: Provider<FakeWayApi>): WayApi =
    WayConfig.SERVER_URL?.let { WayNetwork.api(it, client, json) } ?: fakeWayApi.get()

  @Provides
  @Singleton
  fun providePassengerSocketDataSource(
    client: OkHttpClient,
    json: Json,
    fakeWayApi: Provider<FakeWayApi>,
  ): PassengerSocketDataSource =
    WayConfig.SERVER_URL?.let { PassengerSocketDataSourceImpl(it, client, json) } ?: fakeWayApi.get()
}
