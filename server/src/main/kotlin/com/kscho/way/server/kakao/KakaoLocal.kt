package com.kscho.way.server.kakao

import com.kscho.way.data.remote.LatLngDto
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 좌표가 속한 시도·시군구(카카오 coord2regioncode 의 법정동). [code] 는 법정동 코드 10자리(앞 5자리 = 시군구).
 * [dongs] 는 그 자리의 법정동·행정동 이름(예: 매탄동, 매탄3동) — 상세위치 글에 동 이름이 든 금지구역을 먼저 찾는 데 쓴다.
 */
data class Region(val sido: String, val sigungu: String, val code: String, val dongs: List<String> = emptyList())

/**
 * 장소·주소 → 좌표. 지정 주정차 금지구역의 끝점('○○약국 앞')을 찾는 데 쓴다.
 * 못 찾았거나 물을 수 없으면 빈 목록(또는 null) — 부르는 쪽은 그 구역을 그리지 않는다.
 */
interface PlaceFinder {
  suspend fun region(lat: Double, lng: Double): Region?

  /** [query] 이름의 장소 — ([lat],[lng]) 둘레 [radiusMeters] 안에서. */
  suspend fun keyword(query: String, lat: Double, lng: Double, radiusMeters: Int): List<LatLngDto>

  /** 지번·도로명 주소의 좌표. */
  suspend fun address(query: String): List<LatLngDto>
}

/**
 * 카카오 로컬 API(REST, https://developers.kakao.com/docs/ko/local/dev-guide).
 * 키: 카카오디벨로퍼스 앱의 REST API 키(카카오맵 사용 설정 ON) — 서버 환경 변수 KAKAO_REST_KEY 로만 받는다(저장소·앱에 넣지 않는다).
 *
 * 카카오 정책: 응답 결과를 저장해 다시 쓰면 안 되고 부를 때마다 실시간으로만 쓴다 — 그래서 여기엔 캐시가 없다.
 * 무료량: 주소·키워드 검색 각각 하루 10만 건(넘으면 429) — 하루 [DAILY_LIMIT] 번까지만 부르고, 429 를 받으면 그날은 그만 부른다.
 * 오류는 1분에 한 번만 적는다(키·검색어는 적지 않는다).
 */
class KakaoLocal(
  private val restKey: String,
  private val baseUrl: String = DEFAULT_BASE_URL,
  private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
  private val log: (String) -> Unit = {},
  /** 하루(한국 날짜) 호출 상한 — 무료량(주소·키워드 각 10만)보다 낮게 잡아 남이 마구 불러도 하루치를 다 태우지 않게. */
  private val dailyLimit: Int = DAILY_LIMIT,
  private val clock: () -> Long = System::currentTimeMillis,
) : PlaceFinder {
  private val lastLogged = AtomicLong(0)
  private var day = -1L
  private var used = 0
  /** 429(무료량 소진)를 받은 날은 다시 부르지 않는다. */
  private var exhausted = false

  override suspend fun region(lat: Double, lng: Double): Region? {
    val body = get("/v2/local/geo/coord2regioncode.json?x=$lng&y=$lat") ?: return null
    return parseRegion(body)
  }

  override suspend fun keyword(query: String, lat: Double, lng: Double, radiusMeters: Int): List<LatLngDto> {
    if (query.isBlank()) return emptyList()
    val r = radiusMeters.coerceIn(0, 20_000)
    val body = get("/v2/local/search/keyword.json?query=${enc(query)}&x=$lng&y=$lat&radius=$r&size=$SIZE") ?: return emptyList()
    return parsePoints(body)
  }

  override suspend fun address(query: String): List<LatLngDto> {
    if (query.isBlank()) return emptyList()
    val body = get("/v2/local/search/address.json?query=${enc(query)}&size=$SIZE") ?: return emptyList()
    return parsePoints(body)
  }

  /** 오늘 부른 횟수(로그·확인용). */
  val usedToday: Int
    @Synchronized get() = if (day == today()) used else 0

  @Synchronized
  private fun take(): Boolean {
    val d = today()
    if (d != day) {
      day = d
      used = 0
      exhausted = false
    }
    if (exhausted || used >= dailyLimit) return false
    used++
    return true
  }

  @Synchronized
  private fun markExhausted() {
    exhausted = true
  }

  private fun today(): Long = Math.floorDiv(clock() + KST_OFFSET_MILLIS, DAY_MILLIS)

  private suspend fun get(pathAndQuery: String): String? {
    if (!take()) {
      warn("카카오 로컬 API 오늘 몫을 다 썼어요(상한 $dailyLimit 번 또는 429) — 지정 주정차 금지구역은 내일 다시")
      return null
    }
    val request =
      HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + pathAndQuery))
        .timeout(Duration.ofSeconds(3))
        .header("Authorization", "KakaoAK $restKey")
        .GET()
        .build()
    val response =
      try {
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        warn("카카오 로컬 API 연결 실패: ${e.javaClass.simpleName}")
        return null
      }
    if (response.statusCode() == 200) return response.body()
    if (response.statusCode() == 429) markExhausted()
    warn(
      when (response.statusCode()) {
        401 -> "카카오 로컬 API 401 — REST API 키를 확인해 주세요"
        403 -> "카카오 로컬 API 403 — 앱의 카카오맵 사용 설정(ON)을 확인해 주세요"
        429 -> "카카오 로컬 API 429 — 오늘 무료량을 다 썼어요"
        else -> "카카오 로컬 API ${response.statusCode()}"
      }
    )
    return null
  }

  private fun warn(message: String) {
    val now = System.currentTimeMillis()
    val last = lastLogged.get()
    if (now - last >= 60_000 && lastLogged.compareAndSet(last, now)) log(message)
  }

  @Serializable private data class Points(val documents: List<Point> = emptyList())

  @Serializable private data class Point(val x: String = "", val y: String = "")

  @Serializable private data class Regions(val documents: List<RegionDoc> = emptyList())

  @Serializable
  private data class RegionDoc(
    @SerialName("region_type") val type: String = "",
    val code: String = "",
    @SerialName("region_1depth_name") val sido: String = "",
    @SerialName("region_2depth_name") val sigungu: String = "",
    @SerialName("region_3depth_name") val dong: String = "",
  )

  companion object {
    const val DEFAULT_BASE_URL = "https://dapi.kakao.com"
    const val DAILY_LIMIT = 80_000
    private const val DAY_MILLIS = 86_400_000L
    private const val KST_OFFSET_MILLIS = 9 * 3_600_000L
    private const val SIZE = 5
    private val json = Json { ignoreUnknownKeys = true }

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    /** keyword·address 응답 → 좌표(x 경도, y 위도는 글자로 온다). */
    fun parsePoints(body: String): List<LatLngDto> =
      runCatching { json.decodeFromString<Points>(body).documents }
        .getOrDefault(emptyList())
        .mapNotNull { p ->
          val lng = p.x.toDoubleOrNull() ?: return@mapNotNull null
          val lat = p.y.toDoubleOrNull() ?: return@mapNotNull null
          LatLngDto(lat, lng)
        }

    /** coord2regioncode 응답 → 법정동(B) 기준 시도·시군구. */
    fun parseRegion(body: String): Region? {
      val docs = runCatching { json.decodeFromString<Regions>(body).documents }.getOrDefault(emptyList())
      val d = docs.firstOrNull { it.type == "B" } ?: docs.firstOrNull() ?: return null
      if (d.sido.isBlank()) return null
      return Region(d.sido, d.sigungu, d.code, docs.map { it.dong.trim() }.filter { it.isNotEmpty() }.distinct())
    }
  }
}
