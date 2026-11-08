package com.kscho.way.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class YesNoParserTest {
  private fun assertAll(expected: YesNo, vararg heard: String) {
    heard.forEach { assertEquals("'$it'", expected, YesNoParser.parse(it)) }
  }

  @Test
  fun yes_shortAnswers() {
    assertAll(YesNo.YES, "응", "어", "네", "예", "웅", "응응", "네네", "그래", "그래요", "좋아", "좋아요", "오케이")
  }

  @Test
  fun yes_callRequests_asOnDeviceSttWritesThem() {
    // 에뮬 온디바이스 STT 가 실제로 돌려준 모양: '응 전화해줘'
    assertAll(YesNo.YES, "응 전화해줘", "전화해 줘", "걸어 줘", "전화 걸어", "부탁해", "전화 부탁해요")
  }

  @Test
  fun no_answers() {
    assertAll(YesNo.NO, "아니", "아니요", "아뇨", "아니 괜찮아", "괜찮아요", "됐어", "싫어", "나중에")
  }

  @Test
  fun no_negatedCallRequests_winOverCallWords() {
    assertAll(YesNo.NO, "전화하지 마", "걸지 마", "안 걸어도 돼", "전화 안 해도 돼", "하지 말아 줘")
  }

  @Test
  fun unclear_otherSpeechOrNothing() {
    // '어디야' 의 '어' 처럼 낱말 속 한 글자는 대답으로 보지 않는다
    assertAll(YesNo.UNCLEAR, "", "   ", "어디야", "다음 신고에서 세워줄게", "음")
  }
}
