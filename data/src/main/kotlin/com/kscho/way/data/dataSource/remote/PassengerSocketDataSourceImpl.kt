package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.api.WayNetwork
import com.kscho.way.data.response.PassengerStatusResponse
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class PassengerSocketDataSourceImpl(
  serverUrl: String,
  private val client: OkHttpClient,
  private val json: Json,
  private val reconnectDelayMillis: Long = RECONNECT_DELAY_MILLIS,
) : PassengerSocketDataSource {
  private val apiUrl = WayNetwork.apiBaseUrl(serverUrl).toHttpUrl()

  override fun passengerUpdates(pickupId: String): Flow<PassengerStatusResponse> = flow {
    while (true) {
      try {
        connect(pickupId).collect { emit(it) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: PickupGone) {
        return@flow
      } catch (_: IOException) {
      }
      delay(reconnectDelayMillis)
    }
  }

  private fun connect(pickupId: String): Flow<PassengerStatusResponse> = callbackFlow {
    val url = apiUrl.newBuilder().addPathSegment("pickups").addPathSegment(pickupId).addPathSegment("passenger").build()
    val socket =
      client.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
          override fun onMessage(webSocket: WebSocket, text: String) {
            trySend(json.decodeFromString<PassengerStatusResponse>(text))
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            close(if (code == POLICY_VIOLATION) PickupGone() else IOException("closed $code $reason"))
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            close(IOException("connection failed", t))
          }
        },
      )
    awaitClose { socket.cancel() }
  }

  private class PickupGone : IOException("pickup gone")

  private companion object {
    const val RECONNECT_DELAY_MILLIS = 2_000L
    const val NORMAL_CLOSURE = 1000
    const val POLICY_VIOLATION = 1008
  }
}
