package com.kscho.way.data.api

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object WayNetwork {
  private const val CONNECT_TIMEOUT_SECONDS = 5L
  private const val READ_TIMEOUT_SECONDS = 15L
  private const val PING_INTERVAL_SECONDS = 20L

  val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun client(): OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
      .build()

  fun apiBaseUrl(serverUrl: String): String = "${serverUrl.trimEnd('/')}/api/"

  fun api(serverUrl: String, client: OkHttpClient = client(), json: Json = this.json): WayApi =
    Retrofit.Builder()
      .baseUrl(apiBaseUrl(serverUrl))
      .client(client)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
      .create(WayApi::class.java)
}
