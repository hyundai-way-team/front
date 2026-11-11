package com.kscho.way.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactTest {
  @Test
  fun callName_dropsSurnameOfThreeLetterKoreanName() {
    assertEquals("서연", Contact("1", "이서연", "01000001234").callName)
    assertEquals("지수", Contact("2", "지수", "01000001234").callName)
    assertEquals("남궁민수", Contact("3", "남궁민수", "01000001234").callName)
    assertEquals("Alex", Contact("4", "Alex", "01000001234").callName)
    assertEquals("도윤", CallNames.of("김도윤")) // 운전자 이름도 같은 규칙(앱이 정해 서버에 보낸다)
  }

  @Test
  fun maskedPhone_hidesMiddleDigits() {
    assertEquals("010-****-1234", Contact("1", "이서연", "01000001234").maskedPhone)
    assertEquals("010-****-5678", PhoneNumbers.mask("010-1234-5678"))
    assertEquals("****1234", PhoneNumbers.mask("1234"))
  }

  @Test
  fun displayPhone_spacesDashesLikeWireframe() {
    // 와이어프레임 v2: "010 - 0000 - 0000"
    assertEquals("010 - 0000 - 1234", Contact("1", "이서연", "01000001234").displayPhone)
    assertEquals("010 - 123 - 4567", PhoneNumbers.display("0101234567"))
    assertEquals("02 - 1234 - 5678", PhoneNumbers.display("02-1234-5678"))
    assertEquals("1234", PhoneNumbers.display("1234"))
  }
}
