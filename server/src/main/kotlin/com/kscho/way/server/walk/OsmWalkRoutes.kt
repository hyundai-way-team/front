package com.kscho.way.server.walk

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.response.WalkRouteResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 걷는 길 물음의 답 — '길 없음'(기억해 되묻지 않음)과 '지금 못 물음'(잠시 뒤 다시)을 가른다. */
sealed interface WalkAnswer {
  data class Found(val route: WalkRouteResponse) : WalkAnswer

  /** 경로 서버가 길이 없다고 했다(OSRM NoRoute·NoSegment). */
  data object NoRoute : WalkAnswer

  /** 바쁨·오류·시간 초과(429·5xx·연결 실패) — 답이 아니다. */
  data object Unavailable : WalkAnswer
}

/**
 * 동승자 위치 → 픽업 위치 걷는 길 — 무료 공개 보행 경로 서버(FOSSGIS 의 routing.openstreetmap.de:
 * OpenStreetMap 데이터 · OSRM foot 프로필, 키 없음). 동승자 웹 P-04a·P-05 와 운전자 화면 D-08 이 같은 경로를 그린다.
 *
 * 공개 서비스라 사용 정책(가벼운 사용, 앱을 알리는 User-Agent)을 지킨다 — 부르는 쪽(PickupHub)이 15 m 넘게 움직일 때만 묻고,
 * 못 찾은 자리도 기억해 되묻지 않는다. 동승자 좌표 두 점만 보낸다(이름·번호 없음).
 */
class OsmWalkRoutes(
  private val baseUrl: String = DEFAULT_BASE_URL,
  private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),
) {
  suspend fun route(from: LatLngDto, to: LatLngDto): WalkAnswer {
    val request =
      HttpRequest.newBuilder(URI.create(url(baseUrl, from, to))).timeout(Duration.ofSeconds(6)).header("User-Agent", USER_AGENT).GET().build()
    val response =
      try {
        withContext(Dispatchers.IO) { http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await() }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        return WalkAnswer.Unavailable
      }
    return answerOf(response.statusCode(), response.body())
  }

  @Serializable private data class Answer(val code: String = "", val routes: List<Route> = emptyList())

  @Serializable private data class Code(val code: String = "")

  @Serializable private data class Route(val distance: Double, val duration: Double, val geometry: Line)

  @Serializable private data class Line(val coordinates: List<List<Double>> = emptyList())

  companion object {
    const val DEFAULT_BASE_URL = "https://routing.openstreetmap.de/routed-foot"
    /** 같은 공개 OSRM 의 차 프로필 — 동승자 웹에 차가 오는 길(파란 선)을 그린다. 차 좌표 두 점만 보낸다. */
    const val CAR_BASE_URL = "https://routing.openstreetmap.de/routed-car"
    private const val USER_AGENT = "Way-pickup-demo/1.0 (hyundai-way-team)"
    private val json = Json { ignoreUnknownKeys = true }

    /** OSRM 은 경도,위도 순서. 이 서버는 프로필 자리에 driving 을 쓰지만 foot 경로를 낸다(서버 주소가 프로필). */
    fun url(baseUrl: String, from: LatLngDto, to: LatLngDto): String =
      "${baseUrl.trimEnd('/')}/route/v1/driving/${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson&steps=false"

    /** 상태 코드·본문 → 답. OSRM 은 길이 없으면 200 NoRoute 나 400 NoSegment 를 준다. */
    fun answerOf(status: Int, body: String): WalkAnswer {
      val code = runCatching { json.decodeFromString<Code>(body).code }.getOrDefault("")
      if (code == "NoRoute" || code == "NoSegment") return WalkAnswer.NoRoute
      if (status != 200) return WalkAnswer.Unavailable
      return parse(body)?.let { WalkAnswer.Found(it) } ?: WalkAnswer.Unavailable
    }

    fun parse(body: String): WalkRouteResponse? {
      val answer = runCatching { json.decodeFromString<Answer>(body) }.getOrNull() ?: return null
      if (answer.code != "Ok") return null
      val route = answer.routes.firstOrNull() ?: return null
      val path = route.geometry.coordinates.filter { it.size >= 2 }.map { LatLngDto(it[1], it[0]) }
      if (path.size < 2) return null
      return WalkRouteResponse(path, route.distance.roundToInt(), route.duration.roundToInt())
    }

    /** 두 점 사이 평면 거리(m) — 도심 짧은 거리라 평면으로. */
    fun distanceMeters(a: LatLngDto, b: LatLngDto): Double {
      val dy = (b.lat - a.lat) * 111_195.0
      val dx = (b.lng - a.lng) * 111_195.0 * cos(Math.toRadians(a.lat))
      return sqrt(dx * dx + dy * dy)
    }
  }
}
