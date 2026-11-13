package com.kscho.way.server.sms

import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Test

/** 시연용 — 출발 문자의 링크를 동승자 휴대폰 대신 이 맥 브라우저로 연다(실제 실행은 open, 여기선 가짜). */
class MacBrowserOpenerTest {
  private val runs = Collections.synchronizedList(mutableListOf<List<String>>())

  @Test
  fun firstLinkOfEachPickup_opensOnce() {
    val opener = MacBrowserOpener(run = { runs += it; 0 })
    val outbox = OutboxTextLog(clock = { 0L }, onRecorded = opener::onText)

    outbox.record("pk_1", "이서연", "[WAY] 김도윤 님이 데리러 가고 있어요.\n실시간 위치: http://localhost:8080/p/abc")
    outbox.record("pk_1", "이서연", "[WAY] 곧 도착해요! http://localhost:8080/p/abc")
    outbox.record("pk_2", "박지호", "[WAY] 출발했어요 http://localhost:8080/p/xyz.")
    outbox.record("pk_3", "최민준", "[WAY] 링크 없는 문자")
    opener.drain()

    // 같은 픽업의 다음 문자(ETA 변경·곧 도착)는 탭을 또 열지 않는다. 문장 끝 마침표는 링크에 넣지 않는다
    assertEquals(listOf(listOf("open", "http://localhost:8080/p/abc"), listOf("open", "http://localhost:8080/p/xyz")), runs)
  }

  @Test
  fun browserCanBeChosen() {
    val opener = MacBrowserOpener(app = MacBrowserOpener.appOf("Google Chrome"), run = { runs += it; 0 })
    opener.onText(LoggedText(1, "pk_1", "이서연", "http://localhost:8080/p/abc", 0))
    opener.drain()
    assertEquals(listOf("open", "-a", "Google Chrome", "http://localhost:8080/p/abc"), runs.single())
    // 켜기만 하면(1·true) 기본 브라우저
    assertEquals(null, MacBrowserOpener.appOf("1"))
    assertEquals(null, MacBrowserOpener.appOf("true"))
  }
}
