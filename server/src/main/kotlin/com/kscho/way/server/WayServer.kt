package com.kscho.way.server

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.remote.WayApi
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CompletePickupRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.request.NotificationRequest
import com.kscho.way.server.pickup.PassengerLocationRequest
import com.kscho.way.server.pickup.PickupError
import com.kscho.way.server.pickup.PickupHub
import com.kscho.way.server.pickup.SharingRequest
import com.kscho.way.server.sms.OutboxTextLog
import com.kscho.way.server.tls.RootCaProfile
import com.kscho.way.server.zones.BusStopZones
import com.kscho.way.server.zones.DesignatedZones
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/** 서버가 쓰는 것들. [places] 는 시연 동네(신촌) 장소 목데이터(앱은 이제 오프라인 지도 데이터로 직접 찾는다), [zones] 는 실제 버스정류장 정차 금지 구간. */
class ServerDeps(
  val hub: PickupHub,
  val places: WayApi,
  val outbox: OutboxTextLog,
  /** 앱 assets/map 과 같은 오프라인 지도(way.pmtiles·style.json·fonts·sprites). 없으면 웹 지도가 비어 보인다. */
  val mapDir: File?,
  /** 개발용: 웹 파일을 jar 대신 이 폴더(server/src/main/resources/web)에서 바로 읽는다 — 고치고 새로고침만 하면 된다. */
  val webDir: File? = null,
  /** 시연용 로컬 https 의 인증 기관(CA) 인증서 — 아이폰이 /dev/ca.cer 로 받아 신뢰 등록한다(공개 인증서만, 개인 키 아님). */
  val caCert: File? = null,
  /** 정차 금지 구간 = 실제 버스정류장(공공데이터) 둘레 10m */
  val zones: BusStopZones = BusStopZones.bundled(),
  /** 지정 주정차 금지구역(공공데이터 + 끝점은 카카오 로컬 API 실시간). 카카오 키(KAKAO_REST_KEY)가 없으면 null — 버스정류장만. */
  val designated: DesignatedZones? = null,
  /** 지정 금지구역 계산은 접속 IP 하나에 1분 60번까지(카카오 무료량 지키기) — 넘으면 버스정류장만. */
  val designatedGate: RateGate = RateGate(60, 60_000),
  /** 개발용 경로(/dev/sms 문자 사본 · /api/dev/sms · /dev/ca*). 운영(WAY_DEV_ROUTES=0)에선 끈다 — 문자 사본에 동승자 이름·링크가 있다. */
  val devRoutes: Boolean = true,
)

/** 접속한 쪽 IP — Caddy 뒤라 X-Forwarded-For 의 마지막 값(Caddy 가 붙인 실제 접속 IP), 없으면 직접 접속한 주소. */
internal fun ApplicationCall.clientIp(): String =
  request.headers["X-Forwarded-For"]?.split(',')?.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: request.origin.remoteHost

/** 지정 금지구역(카카오 실시간) 기다리는 최대 시간 — 넘으면 버스정류장만 준다. */
const val DESIGNATED_TIMEOUT_MS = 5_000L

@Serializable data class ErrorDto(val error: String)

@Serializable data class HealthDto(val ok: Boolean)

val WayJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

/**
 * 라우트 모음.
 * - /api/…        운전자 앱(WayApi) — data/remote/WayApi.kt 와 같은 모양
 * - /api/p/{token} 동승자 웹 링크(로그인 없음, 링크 토큰이 곧 권한)
 * - /p/{token}    동승자 웹 페이지 · /map/… 오프라인 지도 · /dev/sms 차가 보낸 문자 사본(시연용)
 */
fun Application.wayModule(deps: ServerDeps) {
  install(ContentNegotiation) { json(WayJson) }
  install(WebSockets) { pingPeriod = 15.seconds }
  // pmtiles 는 파일 일부만(Range) 읽는다
  install(PartialContent)
  install(CallLogging) {
    level = Level.INFO
    // 5초마다 오는 차량 위치·1초마다 오는 동승자 위치는 빼고 굵직한 요청만
    filter { call -> call.request.path().startsWith("/dev/ca") || call.request.path().startsWith("/api/") && !call.request.path().endsWith("/driver-status") && !call.request.path().endsWith("/location") && call.request.path() != "/api/dev/sms" }
    format { call -> "${call.request.httpMethod.value} ${call.request.path()} → ${call.response.status()}" }
  }
  install(StatusPages) {
    exception<PickupError.NotFound> { call, e -> call.respond(HttpStatusCode.NotFound, ErrorDto(e.message ?: "")) }
    exception<PickupError.LinkExpired> { call, e -> call.respond(HttpStatusCode.Gone, ErrorDto(e.message ?: "")) }
    exception<PickupError.NotSharing> { call, e -> call.respond(HttpStatusCode.Conflict, ErrorDto(e.message ?: "")) }
    exception<PickupError.Busy> { call, e -> call.respond(HttpStatusCode.ServiceUnavailable, ErrorDto(e.message ?: "")) }
    exception<BadRequestException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "잘못된 요청")) }
    exception<IllegalArgumentException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "잘못된 요청")) }
  }

  routing {
    route("/api") {
      get("/health") { call.respond(HealthDto(ok = true)) }
      driverRoutes(deps)
      passengerRoutes(deps.hub)
      if (deps.devRoutes) get("/dev/sms") { call.respond(deps.outbox.messages.value) }
    }

    // 동승자 웹 — 링크마다 같은 페이지, 토큰은 주소에서 읽는다
    get("/p/{token}") { call.respondPage(deps.webDir, "index.html") }
    if (deps.webDir != null) staticFiles("/web", deps.webDir) else staticResources("/web", "web")
    deps.mapDir?.let { staticFiles("/map", it) }

    if (deps.devRoutes) {
      // 개발용: 차가 보낸 문자 사본 보기(동승자 휴대폰 문자 앱 흉내)
      get("/dev/sms") { call.respondPage(deps.webDir, "outbox.html") }
      // 개발용: 로컬 https 인증서(CA) 받기 — 아이폰 사파리로 .mobileconfig 를 열면 '프로파일 다운로드됨 → 설정에서 설치'
      get("/dev/ca.mobileconfig") {
        val ca = deps.caCert?.takeIf { it.isFile } ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(RootCaProfile.mobileConfig(ca.readBytes()), ContentType.parse(RootCaProfile.CONTENT_TYPE))
      }
      get("/dev/ca.cer") {
        val ca = deps.caCert?.takeIf { it.isFile } ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondBytes(ca.readBytes(), ContentType("application", "x-x509-ca-cert"))
      }
      get("/") { call.respondRedirect("/dev/sms") }
    }
  }
}

private fun Route.driverRoutes(deps: ServerDeps) {
  val hub = deps.hub
  val places = deps.places

  get("/places") { call.respond(places.searchPlaces(call.query("query"))) }
  get("/places/reverse") { call.respond(places.describeLocation(call.latLng())) }
  // 주변 정차 금지 구간 데이터만 — 지도에 그리기·정차 금지 판단은 앱이 한다(도로·장소는 앱 속 오프라인 지도)
  get("/stop-zones") {
    val at = call.latLng()
    val radius = (call.query("radius").toIntOrNull() ?: 300).coerceIn(1, 5_000)
    val bus = deps.zones.near(at.lat, at.lng, radius)
    // 카카오가 느리거나 안 되면 버스정류장만이라도 바로 준다
    val designated =
      deps.designated
        ?.takeIf { deps.designatedGate.allow(call.clientIp()) }
        ?.let { d -> withTimeoutOrNull(DESIGNATED_TIMEOUT_MS) { d.near(at.lat, at.lng, radius) } }
        .orEmpty()
    call.respond(bus + designated)
  }

  route("/pickups") {
    post { call.respond(hub.create(call.receive<CreatePickupRequest>())) }

    // 운전자 앱: 동승자 링크 확인·위치 공유·위치 — 연결하면 지금 상태부터 보낸다
    webSocket("/{id}/passenger") {
      val feed =
        try {
          hub.driverFeed(call.parameters["id"].orEmpty())
        } catch (e: PickupError) {
          close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message ?: ""))
          return@webSocket
        }
      val sender = launch { feed.collect { outgoing.send(Frame.Text(WayJson.encodeToString(it))) } }
      for (frame in incoming) Unit // 운전자 앱은 듣기만 — 연결이 닫힐 때까지 기다린다
      sender.cancel()
    }
    post("/{id}/driver-status") {
      hub.reportDriver(call.id(), call.receive<DriverStatusRequest>())
      call.respond(HttpStatusCode.NoContent)
    }
    post("/{id}/notifications") {
      hub.notify(call.id(), call.receive<NotificationRequest>())
      call.respond(HttpStatusCode.NoContent)
    }
    post("/{id}/arrival") {
      hub.arrival(call.id(), call.receive<ArrivalRequest>())
      call.respond(HttpStatusCode.NoContent)
    }
    post("/{id}/complete") {
      hub.complete(call.id(), call.receive<CompletePickupRequest>())
      call.respond(HttpStatusCode.NoContent)
    }
  }
}

private fun Route.passengerRoutes(hub: PickupHub) {
  route("/p/{token}") {
    get { call.respond(hub.view(call.token())) }

    webSocket("/live") {
      val feed =
        try {
          hub.viewFeed(call.parameters["token"].orEmpty())
        } catch (e: PickupError) {
          close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message ?: ""))
          return@webSocket
        }
      val sender = launch { feed.collect { outgoing.send(Frame.Text(WayJson.encodeToString(it))) } }
      for (frame in incoming) Unit
      sender.cancel()
    }
    post("/open") {
      hub.openLink(call.token())
      call.respond(HttpStatusCode.NoContent)
    }
    post("/sharing") {
      hub.setSharing(call.token(), call.receive<SharingRequest>().allow)
      call.respond(HttpStatusCode.NoContent)
    }
    post("/late") {
      hub.markLate(call.token())
      call.respond(HttpStatusCode.NoContent)
    }
    post("/location") {
      val body = call.receive<PassengerLocationRequest>()
      if (body.lat !in -90.0..90.0 || body.lng !in -180.0..180.0) throw BadRequestException("좌표가 이상해요")
      hub.updateLocation(call.token(), LatLngDto(body.lat, body.lng))
      call.respond(HttpStatusCode.NoContent)
    }
  }
}

private suspend fun ApplicationCall.respondPage(webDir: File?, name: String) {
  val html = webDir?.resolve(name)?.takeIf { it.isFile }?.readText() ?: WayServerResources.text("web/$name") ?: return respond(HttpStatusCode.NotFound)
  response.cacheControl(CacheControl.NoCache(null))
  respondText(html, ContentType.Text.Html)
}

private object WayServerResources {
  fun text(path: String): String? = javaClass.classLoader.getResource(path)?.readText()
}

private fun ApplicationCall.query(name: String) = request.queryParameters[name].orEmpty()

private fun ApplicationCall.latLng(): LatLngDto {
  val lat = query("lat").toDoubleOrNull()
  val lng = query("lng").toDoubleOrNull()
  if (lat == null || lng == null) throw BadRequestException("lat·lng 가 필요해요")
  return LatLngDto(lat, lng)
}

private fun ApplicationCall.id() = parameters["id"].orEmpty()

private fun ApplicationCall.token() = parameters["token"].orEmpty()
