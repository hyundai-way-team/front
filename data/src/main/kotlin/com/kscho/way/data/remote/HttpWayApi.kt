package com.kscho.way.data.remote

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

/** 서버가 2xx 가 아닌 답을 줬다(404 없는 픽업 · 410 끝난 픽업 등). */
class WayApiException(val code: Int, message: String) : IOException("Way 서버 $code: $message")

/**
 * [WayApi] 의 HTTP 구현 — 로컬 서버(:server)나 나중의 실제 서버에 붙는다.
 * 요청·응답 모양은 [Dtos] 그대로, 경로는 서버 /api 아래.
 */
class HttpWayApi(
  baseUrl: String,
  private val client: OkHttpClient = defaultClient(),
  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  },
  /** 동승자 상태 연결이 끊겼을 때 다시 붙기까지 */
  private val reconnectDelayMillis: Long = 2_000,
) : WayApi {
  private val api: HttpUrl = "${baseUrl.trimEnd('/')}/api/".toHttpUrl()

  // ── 장소·지도

  override suspend fun searchPlaces(query: String): List<PlaceDto> = get("places", "query" to query)

  override suspend fun describeLocation(location: LatLngDto): LocationDescriptionDto = get("places/reverse", *location.query())

  override suspend fun stopZones(center: LatLngDto, radiusMeters: Int): List<ZoneDto> =
    get("stop-zones", *center.query(), "radius" to radiusMeters.toString())


  // ── 픽업 세션

  override suspend fun createPickup(request: CreatePickupRequest): CreatePickupResponse =
    json.decodeFromString(post("pickups", json.encodeToString(request)))

  /**
   * WebSocket 으로 동승자 상태를 받는다. 연결하면 지금 상태부터 오고, 끊기면 [reconnectDelayMillis] 뒤 다시 붙는다.
   * 서버가 픽업을 모른다고 닫으면(재시작 등) 흐름을 끝낸다.
   */
  override fun passengerUpdates(pickupId: String): Flow<PassengerStatusDto> = flow {
    while (true) {
      try {
        connect(pickupId).collect { emit(it) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: PickupGone) {
        return@flow
      } catch (_: IOException) {
        // 잠깐 끊김 — 다시 붙는다
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

  /** 앱이 끝날 때 연결·스레드를 정리한다(테스트용). */
  fun close() {
    client.dispatcher.executorService.shutdown()
    client.connectionPool.evictAll()
  }

  // ── 내부

  private class PickupGone : IOException("픽업이 서버에 없어요")

  private fun connect(pickupId: String): Flow<PassengerStatusDto> = callbackFlow {
    val url = api.newBuilder().addPathSegments("pickups").addPathSegment(pickupId).addPathSegment("passenger").build()
    val socket =
      client.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
          override fun onMessage(webSocket: WebSocket, text: String) {
            trySend(json.decodeFromString<PassengerStatusDto>(text))
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

  private suspend fun execute(request: Request): String =
    client.newCall(request).await().use { res ->
      val text = res.body?.string().orEmpty()
      if (!res.isSuccessful) throw WayApiException(res.code, text.ifBlank { res.message })
      text
    }

  private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
      object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)

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
