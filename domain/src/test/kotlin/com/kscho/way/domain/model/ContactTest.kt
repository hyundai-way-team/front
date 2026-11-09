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
  }

  @Test
  fun maskedPhone_hidesMiddleDigits() {
    assertEquals("010-****-1234", Contact("1", "이서연", "01000001234").maskedPhone)
    assertEquals("010-****-5678", PhoneNumbers.mask("010-1234-5678"))
    assertEquals("****1234", PhoneNumbers.mask("1234"))
  }
}
