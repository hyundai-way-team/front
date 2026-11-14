package com.kscho.way.server.kakao

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 카카오 로컬 API 클라이언트 — 진짜 서버 대신 이 컴퓨터 안의 작은 HTTP 서버로. */
class KakaoLocalTest {
  private val seen = mutableListOf<Pair<String, String?>>() // 경로+물음, Authorization
  private var status = 200
  private var body = "{}"
  private val server =
    HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
      createContext("/") { ex ->
        seen += URLDecoder.decode(ex.requestURI.toString(), Charsets.UTF_8) to ex.requestHeaders.getFirst("Authorization")
        val bytes = body.toByteArray()
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
      }
      start()
    }
  private val logs = mutableListOf<String>()
  private val kakao = KakaoLocal("test-key", baseUrl = "http://127.0.0.1:${server.address.port}", log = { logs += it })

  @After
  fun stop() = server.stop(0)

  @Test
  fun keyword_sendsKeyAndArea_andReadsXAsLongitude() = runTest {
    body = """{"meta":{"total_count":1},"documents":[{"place_name":"가람약국","x":"126.9426","y":"37.5567"}]}"""

    val got = kakao.keyword("가람약국", 37.5, 127.0, 1300)

    assertEquals(listOf(37.5567 to 126.9426), got.map { it.lat to it.lng })
    val (path, auth) = seen.single()
    assertEquals("KakaoAK test-key", auth)
    assertTrue(path.startsWith("/v2/local/search/keyword.json?query=가람약국&x=127.0&y=37.5&radius=1300"))
  }

  @Test
  fun address_readsDocuments() = runTest {
    body = """{"documents":[{"address_name":"경기 수원시 영통구 매탄동 1175-13","x":"127.04","y":"37.26"},{"x":"","y":""}]}"""

    assertEquals(listOf(37.26 to 127.04), kakao.address("경기도 수원시 매탄3동 1175-13").map { it.lat to it.lng })
    assertTrue(seen.single().first.startsWith("/v2/local/search/address.json?query=경기도 수원시 매탄3동 1175-13"))
  }

  @Test
  fun region_prefersLegalDong() = runTest {
    body =
      """{"documents":[
        {"region_type":"H","code":"4128151000","region_1depth_name":"경기도","region_2depth_name":"고양시 덕양구","region_3depth_name":"화정1동"},
        {"region_type":"B","code":"4128110100","region_1depth_name":"경기도","region_2depth_name":"고양시 덕양구","region_3depth_name":"화정동"}]}"""

    assertEquals(Region("경기도", "고양시 덕양구", "4128110100", listOf("화정1동", "화정동")), kakao.region(37.63, 126.83))
    assertTrue(seen.single().first.startsWith("/v2/local/geo/coord2regioncode.json?x=126.83&y=37.63"))
  }

  @Test
  fun sameQuestion_isAskedAgain_noStoredAnswers() = runTest {
    body = """{"documents":[{"x":"127.0","y":"37.5"}]}"""

    kakao.keyword("가람약국", 37.5, 127.0, 500)
    kakao.keyword("가람약국", 37.5, 127.0, 500)

    // 카카오 정책: 실시간으로만 — 같은 물음도 다시 보낸다
    assertEquals(2, seen.size)
  }

  @Test
  fun quotaExceeded_givesNothing_logsOnce_andStopsForTheDay() = runTest {
    var now = 1_800_000_000_000L
    val k = KakaoLocal("test-key", baseUrl = "http://127.0.0.1:${server.address.port}", log = { logs += it }, clock = { now })
    status = 429
    body = """{"errorType":"RequestThrottled","message":"API limit has been exceeded."}"""

    assertTrue(k.keyword("가람약국", 37.5, 127.0, 500).isEmpty())
    assertNull(k.region(37.5, 127.0))

    // 429 를 받은 날은 더 보내지 않는다
    assertEquals(1, seen.size)
    assertEquals(1, logs.size)
    assertTrue(logs.single().contains("429"))
    // 다음 날 다시
    now += 86_400_000L
    status = 200
    body = """{"documents":[]}"""
    k.region(37.5, 127.0)
    assertEquals(2, seen.size)
  }

  @Test
  fun dailyLimit_stopsCallingUntilTomorrow() = runTest {
    var now = 1_800_000_000_000L
    val k = KakaoLocal("test-key", baseUrl = "http://127.0.0.1:${server.address.port}", dailyLimit = 2, clock = { now })
    body = """{"documents":[]}"""

    repeat(3) { k.keyword("가람약국", 37.5, 127.0, 500) }
    assertEquals(2, seen.size)
    assertEquals(2, k.usedToday)

    now += 86_400_000L
    k.keyword("가람약국", 37.5, 127.0, 500)
    assertEquals(3, seen.size)
  }

  @Test
  fun blankQuery_isNotSent() = runTest {
    assertTrue(kakao.keyword(" ", 37.5, 127.0, 500).isEmpty())
    assertTrue(kakao.address("").isEmpty())
    assertTrue(seen.isEmpty())
  }
}
