package com.kscho.way.server

import com.kscho.way.data.remote.CompletePickupRequest
import com.kscho.way.data.remote.CreatePickupRequest
import com.kscho.way.data.remote.CreatePickupResponse
import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.PassengerStatusDto
import com.kscho.way.data.remote.SharingWire
import com.kscho.way.server.pickup.PassengerViewDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WayServerTest {
  private val mapDir = System.getProperty("way.mapDir")?.let(::File)

  private val request =
    CreatePickupRequest(
      passengerName = "이서연",
      passengerCallName = "서연",
      placeName = "신촌 XX 빌라",
      pickupPoint = LatLngDto(37.5567, 126.942),
      pickupTitle = "신촌 XX 빌라 골목 입구 앞",
      pickupDetail = "신촌로 · 도로 북쪽(빌라 쪽) 인도",
      arrivalAtEpochMillis = 1_800_000_000_000,
      vehicleDescription = "흰색 아반떼",
      plateNumber = "12가 3456",
      driverName = "김도윤",
      driverCallName = "도윤",
    )

  private fun server(block: suspend ApplicationTestBuilder.(ServerDeps) -> Unit) = testApplication {
    val deps = createDeps("http://localhost:8080", mapDir)
    application { wayModule(deps) }
    block(deps)
  }

  private suspend fun HttpClient.postJson(path: String, body: String) =
    post(path) {
      contentType(ContentType.Application.Json)
      setBody(body)
    }

  private suspend fun HttpClient.createPickup(): CreatePickupResponse {
    val res = postJson("/api/pickups", WayJson.encodeToString(request))
    assertEquals(HttpStatusCode.OK, res.status)
    return WayJson.decodeFromString(res.bodyAsText())
  }

  private fun CreatePickupResponse.token() = shareUrl.substringAfterLast("/p/")

  @Test
  fun departure_textsLink_andPassengerPageOpens() = server { deps ->
    val created = client.createPickup()

    assertTrue(deps.outbox.messages.value.single().text.contains(created.shareUrl))
    // 개발용 발송함(동승자 휴대폰 대신)에서도 보인다
    val outbox = client.get("/api/dev/sms")
    assertEquals(HttpStatusCode.OK, outbox.status)
    assertTrue(outbox.bodyAsText().contains(created.token()))
    val page = client.get("/p/${created.token()}")
    assertEquals(HttpStatusCode.OK, page.status)
    assertTrue(page.bodyAsText().contains("/web/app.js"))
    assertEquals(HttpStatusCode.OK, client.get("/web/app.js").status)
    val view: PassengerViewDto = WayJson.decodeFromString(client.get("/api/p/${created.token()}").bodyAsText())
    assertEquals("도윤", view.driverCallName)
    assertEquals("EN_ROUTE", view.phase)
  }

  @Test
  fun passengerSharing_reachesDriverOverWebSocket() = server { _ ->
    val created = client.createPickup()
    val token = created.token()
    val ws = createClient { install(WebSockets) }

    ws.webSocket("/api/pickups/${created.pickupId}/passenger") {
      val first: PassengerStatusDto = WayJson.decodeFromString((incoming.receive() as Frame.Text).readText())
      assertEquals(PassengerStatusDto(), first)

      assertEquals(HttpStatusCode.NoContent, client.postJson("/api/p/$token/open", "{}").status)
      assertEquals(HttpStatusCode.NoContent, client.postJson("/api/p/$token/sharing", """{"allow":true}""").status)
      assertEquals(HttpStatusCode.NoContent, client.postJson("/api/p/$token/location", """{"lat":37.5571,"lng":126.942}""").status)

      var last = first
      while (last.location == null) last = WayJson.decodeFromString((incoming.receive() as Frame.Text).readText())
      assertTrue(last.linkOpened)
      assertEquals(SharingWire.SHARING, last.sharing)
      assertEquals(LatLngDto(37.5571, 126.942), last.location)
    }
  }

  @Test
  fun afterBoarding_linkIsExpired() = server { _ ->
    val created = client.createPickup()
    val token = created.token()
    client.postJson("/api/p/$token/sharing", """{"allow":true}""")
    client.postJson("/api/pickups/${created.pickupId}/complete", WayJson.encodeToString(CompletePickupRequest(boarded = true, pickupLocationChanges = 0)))

    assertEquals(HttpStatusCode.Gone, client.postJson("/api/p/$token/location", """{"lat":37.5571,"lng":126.942}""").status)
    val view: PassengerViewDto = WayJson.decodeFromString(client.get("/api/p/$token").bodyAsText())
    assertEquals("BOARDED", view.phase)
    assertEquals(SharingWire.ENDED, view.sharing)
  }

  @Test
  fun unknownLink_isNotFound() = server { _ ->
    assertEquals(HttpStatusCode.NotFound, client.get("/api/p/nope").status)
    assertEquals(HttpStatusCode.NotFound, client.postJson("/api/pickups/nope/arrival", """{"arrivedAtEpochMillis":0,"waitSeconds":60}""").status)
  }

  @Test
  fun offlineMap_servesByteRanges() = server { _ ->
    // pmtiles 는 파일 전체가 아니라 필요한 부분만 Range 로 읽는다
    val res = client.get("/map/way.pmtiles") { header(HttpHeaders.Range, "bytes=0-6") }
    assertEquals(HttpStatusCode.PartialContent, res.status)
    assertEquals("PMTiles", res.bodyAsBytes().decodeToString())
    assertEquals(HttpStatusCode.OK, client.get("/map/style.json").status)
  }

  @Test
  fun placeSearch_usesDemoNeighborhood() = server { _ ->
    val body = client.get("/api/places?query=xx").bodyAsText()
    assertTrue(body.contains("신촌 XX 빌라"))
    // 앱이 할 일(내 차 정보 · 정차 금지 판단 · 지난 픽업 · 지도 도로·장소)은 서버에 없다
    assertEquals(HttpStatusCode.NotFound, client.get("/api/me/vehicle").status)
    assertEquals(HttpStatusCode.NotFound, client.get("/api/stop-restrictions/check?lat=37.5&lng=126.9&arrivalAt=0").status)
    assertEquals(HttpStatusCode.NotFound, client.get("/api/pickups/recent").status)
    assertEquals(HttpStatusCode.NotFound, client.get("/api/map/features?lat=37.556783&lng=126.942649&radius=200").status)
    val zones = client.get("/api/stop-zones?lat=37.556783&lng=126.942649&radius=200").bodyAsText()
    // 실제 버스정류장(공공데이터) 둘레 10m 구간 — 목데이터 구간이 아니다
    assertTrue(zones.startsWith("[") && zones.contains("\"bus-") && zones.contains("버스정류장") && !zones.contains("\"roads\""))
  }

  @Test
  fun caCert_isServedForPhone_onlyWhenHttpsIsOn() {
    server { _ -> assertEquals(HttpStatusCode.NotFound, client.get("/dev/ca.cer").status) }
    val ca = File.createTempFile("way-ca", ".cer").apply { writeBytes(byteArrayOf(0x30, 0x03, 1, 2, 3)); deleteOnExit() }
    testApplication {
      val deps = createDeps("https://localhost:8443", mapDir, caCert = ca)
      application { wayModule(deps) }
      val res = client.get("/dev/ca.cer")
      assertEquals(HttpStatusCode.OK, res.status)
      assertEquals("application/x-x509-ca-cert", res.headers[HttpHeaders.ContentType])
      assertEquals(5, res.bodyAsBytes().size)
      // 아이폰 사파리는 구성 프로파일로 받아야 설치 안내를 띄운다
      val profile = client.get("/dev/ca.mobileconfig")
      assertEquals("application/x-apple-aspen-config", profile.headers[HttpHeaders.ContentType]?.substringBefore(';'))
      val body = profile.bodyAsText()
      assertTrue(body.contains("<string>com.apple.security.root</string>"))
      assertTrue(body.contains(java.util.Base64.getEncoder().encodeToString(ca.readBytes())))
    }
  }

  @Test
  fun production_hidesDevRoutes_butKeepsHealth() = testApplication {
    // 운영(WAY_DEV_ROUTES=0): 문자 사본(동승자 이름·링크)·개발 화면을 밖에 내놓지 않는다
    val deps = createDeps("https://way.example", mapDir, devRoutes = false)
    application { wayModule(deps) }
    client.createPickup()
    assertEquals(HttpStatusCode.NotFound, client.get("/api/dev/sms").status)
    assertEquals(HttpStatusCode.NotFound, client.get("/dev/sms").status)
    assertEquals(HttpStatusCode.NotFound, client.get("/dev/ca.cer").status)
    assertEquals(HttpStatusCode.NotFound, client.get("/").status)
    val health = client.get("/api/health")
    assertEquals(HttpStatusCode.OK, health.status)
    assertTrue(health.bodyAsText().contains("\"ok\":true"))
  }

}
