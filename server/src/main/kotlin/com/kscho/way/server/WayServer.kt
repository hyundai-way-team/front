package com.kscho.way.server

import com.kscho.way.data.remote.ArrivalRequest
import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.DriverStatusRequest
import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.NotificationRequest
import com.kscho.way.data.remote.WayApi
import com.kscho.way.server.pickup.PassengerLocationRequest
import com.kscho.way.server.pickup.PickupError
import com.kscho.way.server.pickup.PickupHub
import com.kscho.way.server.pickup.SharingRequest
import com.kscho.way.server.sms.OutboxTextLog
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
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/** 서버가 쓰는 것들. [places] 는 시연 동네(신촌) 장소·정차 금지 구간 — 실제 장소 API 를 정하면 바뀐다. */
class ServerDeps(
  val hub: PickupHub,
  val places: WayApi,
  val outbox: OutboxTextLog,
  /** 앱 assets/map 과 같은 오프라인 지도(way.pmtiles·style.json·fonts·sprites). 없으면 웹 지도가 비어 보인다. */
  val mapDir: File?,
  /** 개발용: 웹 파일을 jar 대신 이 폴더(server/src/main/resources/web)에서 바로 읽는다 — 고치고 새로고침만 하면 된다. */
  val webDir: File? = null,
)

@Serializable data class ErrorDto(val error: String)

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
    filter { call -> call.request.path().startsWith("/api/") && !call.request.path().endsWith("/driver-status") && !call.request.path().endsWith("/location") && call.request.path() != "/api/dev/sms" }
    format { call -> "${call.request.httpMethod.value} ${call.request.path()} → ${call.response.status()}" }
  }
  install(StatusPages) {
    exception<PickupError.NotFound> { call, e -> call.respond(HttpStatusCode.NotFound, ErrorDto(e.message ?: "")) }
    exception<PickupError.LinkExpired> { call, e -> call.respond(HttpStatusCode.Gone, ErrorDto(e.message ?: "")) }
    exception<PickupError.NotSharing> { call, e -> call.respond(HttpStatusCode.Conflict, ErrorDto(e.message ?: "")) }
    exception<BadRequestException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "잘못된 요청")) }
    exception<IllegalArgumentException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "잘못된 요청")) }
  }

  routing {
    route("/api") {
      driverRoutes(deps)
      passengerRoutes(deps.hub)
      get("/dev/sms") { call.respond(deps.outbox.messages.value) }
    }

    // 동승자 웹 — 링크마다 같은 페이지, 토큰은 주소에서 읽는다
    get("/p/{token}") { call.respondPage(deps.webDir, "index.html") }
    if (deps.webDir != null) staticFiles("/web", deps.webDir) else staticResources("/web", "web")
    deps.mapDir?.let { staticFiles("/map", it) }

    // 개발용: 차가 보낸 문자 사본 보기(동승자 휴대폰 문자 앱 흉내)
    get("/dev/sms") { call.respondPage(deps.webDir, "outbox.html") }
    get("/") { call.respondRedirect("/dev/sms") }
  }
}

private fun Route.driverRoutes(deps: ServerDeps) {
  val hub = deps.hub
  val places = deps.places

  get("/places") { call.respond(places.searchPlaces(call.query("query"))) }
  get("/places/reverse") { call.respond(places.describeLocation(call.latLng())) }
  get("/map/features") { call.respond(places.mapFeatures(call.latLng(), call.query("radius").toIntOrNull() ?: 300)) }
  get("/stop-restrictions/check") {
    val arrivalAt = call.query("arrivalAt").toLongOrNull() ?: throw BadRequestException("arrivalAt 이 필요해요")
    call.respond(places.checkStopRestriction(call.latLng(), arrivalAt))
  }
  get("/me/vehicle") { call.respond(places.myVehicle()) }

  route("/pickups") {
    post { call.respond(hub.create(call.receive<CreatePickupRequest>())) }
    get("/recent") { call.respond(hub.recent(call.query("limit").toIntOrNull() ?: 5)) }

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
