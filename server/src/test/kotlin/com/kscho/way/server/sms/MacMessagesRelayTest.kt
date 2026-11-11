package com.kscho.way.server.sms

import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시연용 문자 내보내기 — 서버가 남긴 문자 사본을 맥 메시지 앱으로(실제 발송은 osascript, 여기선 가짜). */
class MacMessagesRelayTest {
  private val runs = Collections.synchronizedList(mutableListOf<List<String>>())

  @Test
  fun copiesOfCarTexts_goToConfiguredPhone_inOrder() {
    val relay = MacMessagesRelay("010-1234-5678", runScript = { _, args -> runs += args; 0 })
    val outbox = OutboxTextLog(clock = { 0L }, onRecorded = { relay.send(it.text) })

    outbox.record("pk_1", "이서연", "[WAY] 김도윤 님이 \"데리러\" 가고 있어요.")
    outbox.record("pk_1", "이서연", "[WAY] 곧 도착해요!")
    relay.drain()

    // 문자 내용은 인자로 — 따옴표가 있어도 스크립트가 깨지지 않는다. 번호는 숫자만
    // 기본은 iMessage(자기 번호로 보내는 SMS 는 실패해서)
    assertEquals(
      listOf(listOf("[WAY] 김도윤 님이 \"데리러\" 가고 있어요.", "01012345678", "iMessage"), listOf("[WAY] 곧 도착해요!", "01012345678", "iMessage")),
      runs,
    )
    assertEquals("010-****-5678", relay.maskedNumber)
  }

  @Test
  fun via_canBeSms() {
    val relay = MacMessagesRelay("01012345678", via = MacMessagesRelay.Via.of("SMS"), runScript = { _, args -> runs += args; 0 })
    relay.send("[WAY] 곧 도착해요!")
    relay.drain()
    assertEquals("SMS", runs.single()[2])
    assertEquals(MacMessagesRelay.Via.IMESSAGE, MacMessagesRelay.Via.of(null))
    assertTrue(MacMessagesRelay.SCRIPT.contains("send theText to participant theNumber of chosen"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun oddNumber_isRejectedAtStart() {
    MacMessagesRelay("1234")
  }
}
