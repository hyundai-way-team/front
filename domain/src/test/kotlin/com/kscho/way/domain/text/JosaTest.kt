package com.kscho.way.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class JosaTest {
  @Test
  fun hangulWithFinalConsonant_takesEuro() {
    assertEquals("연남동 OO 식당으로", Josa.ro("연남동 OO 식당"))
    assertEquals("서울역으로", Josa.ro("서울역"))
    assertEquals("광화문으로", Josa.ro("광화문"))
  }

  @Test
  fun hangulWithoutFinalConsonantOrRieul_takesRo() {
    assertEquals("홍대입구로", Josa.ro("홍대입구"))
    assertEquals("명동길로", Josa.ro("명동길"))
  }

  @Test
  fun latinAndDigits_followHowTheyAreRead() {
    assertEquals("Starbucks로", Josa.ro("Starbucks"))
    assertEquals("GS25로", Josa.ro("GS25")) // 오
    assertEquals("Hotel로", Josa.ro("Hotel")) // ㄹ
    assertEquals("Station으로", Josa.ro("Station")) // ㄴ
    assertEquals("Museum으로", Josa.ro("Museum")) // ㅁ
    assertEquals("3번 출구 13으로", Josa.ro("3번 출구 13")) // 삼
    assertEquals("Bundang으로", Josa.ro("Bundang")) // ㅇ
    assertEquals("Parking으로", Josa.ro("Parking"))
  }

  @Test
  fun trailingBracketsAndPunctuation_areSkipped() {
    assertEquals("스타벅스(신촌점)으로", Josa.ro("스타벅스(신촌점)"))
    assertEquals("CU 신촌.으로", Josa.ro("CU 신촌.")) // 촌
    assertEquals("", Josa.ro(""))
  }
}
