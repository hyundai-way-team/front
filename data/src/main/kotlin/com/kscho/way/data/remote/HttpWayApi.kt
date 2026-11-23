package com.kscho.way.data.remote

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.data.response.CreatePickupResponse
import com.kscho.way.data.response.LocationDescriptionResponse
import com.kscho.way.data.response.PassengerStatusResponse
import com.kscho.way.data.response.PlaceResponse
import com.kscho.way.data.response.StopZoneResponse
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WayApiException(val code: Int, message: String) : IOException("Way 서버 $code: $message")

class HttpWayApi(
  baseUrl: String,
  private val client: OkHttpClient = defaultClient(),
  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  },
  private val reconnectDelayMillis: Long = 2_000,
) : WayApi {
  private val api: HttpUrl = "${baseUrl.trimEnd('/')}/api/".toHttpUrl()

  override suspend fun searchPlaces(query: String): List<PlaceResponse> = get("places", "query" to query)

  override suspend fun describeLocation(location: LatLngDto): LocationDescriptionResponse = get("places/reverse", *location.query())

  override suspend fun stopZones(center: LatLngDto, radiusMeters: Int): List<StopZoneResponse> =
    get("stop-zones", *center.query(), "radius" to radiusMeters.toString())

  override suspend fun createPickup(request: CreatePickupRequest): CreatePickupResponse =
    json.decodeFromString(post("pickups", json.encodeToString(request)))

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

  override suspend fun reportDriver(pickupId: String, request: DriverStatusRequest) {
    post("pickups/$pickupId/driver-status", json.encodeToString(request))
  }

  override suspend fun sendNotification(pickupId: String, request: NotificationRequest) {
    post("pickups/$pickupId/notifications", json.encodeToString(request))
  }

  override suspend fun reportArrival(pickupId: String, request: ArrivalRequest) {
    post("pickups/$pickupId/arrival", json.encodeToString(request))
  }

  override suspend fun completePickup(pickupId: String, request: CompletePickupRequest) {
    post("pickups/$pickupId/complete", json.encodeToString(request))
  }

  fun close() {
    client.dispatcher.executorService.shutdown()
    client.connectionPool.evictAll()
  }

  private class PickupGone : IOException("픽업이 서버에 없어요")

  private fun connect(pickupId: String): Flow<PassengerStatusResponse> = callbackFlow {
    val url = api.newBuilder().addPathSegments("pickups").addPathSegment(pickupId).addPathSegment("passenger").build()
    val socket =
      client.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
          override fun onMessage(webSocket: WebSocket, text: String) {
            trySend(json.decodeFromString<PassengerStatusResponse>(text))
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            close(if (code == POLICY_VIOLATION) PickupGone() else IOException("닫힘 $code $reason"))
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            close(IOException("연결 실패", t))
          }
        },
      )
    awaitClose { socket.cancel() }
  }

  private suspend inline fun <reified T> get(path: String, vararg query: Pair<String, String>): T {
    val url = api.newBuilder().addPathSegments(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
    return json.decodeFromString(execute(Request.Builder().url(url).get().build()))
  }

  private suspend fun post(path: String, body: String): String {
    val url = api.newBuilder().addPathSegments(path).build()
    return execute(Request.Builder().url(url).post(body.toRequestBody(JSON)).build())
  }

  private suspend fun execute(request: Request): String {
    val res = client.newCall(request).await()
    if (!res.ok) throw WayApiException(res.code, res.text.ifBlank { res.message })
    return res.text
  }

  private class Answer(val code: Int, val ok: Boolean, val message: String, val text: String)

  private suspend fun Call.await(): Answer = suspendCancellableCoroutine { cont ->
    enqueue(
      object : Callback {
        override fun onResponse(call: Call, response: Response) {
          val answer =
            try {
              response.use { Answer(it.code, it.isSuccessful, it.message, it.body?.string().orEmpty()) }
            } catch (e: IOException) {
              if (!cont.isCancelled) cont.resumeWithException(e)
              return
            }
          cont.resume(answer)
        }

        override fun onFailure(call: Call, e: IOException) {
          if (!cont.isCancelled) cont.resumeWithException(e)
        }
      }
    )
    cont.invokeOnCancellation { cancel() }
  }

  private fun LatLngDto.query() = arrayOf("lat" to lat.toString(), "lng" to lng.toString())

  companion object {
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val NORMAL_CLOSURE = 1000
    private const val POLICY_VIOLATION = 1008

    fun defaultClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
  }
}
