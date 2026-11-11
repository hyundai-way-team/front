package com.kscho.way.domain.model

/** 픽업할 사람(MVP: 동승자 1명). 전화번호는 차량 밖 안내(Gleo 응답 등)에 넣지 않는다. */
data class Contact(val id: String, val name: String, val phone: String) {
  /** 화면·음성에서 부르는 이름. "이서연" → "서연" (성+이름 세 글자 한글일 때만 성을 뗀다). */
  val callName: String
    get() = CallNames.of(name)

  val maskedPhone: String
    get() = PhoneNumbers.mask(phone)

  /** 차 화면에 보이는 번호 "010 - 0000 - 1234"(와이어프레임 v2). */
  val displayPhone: String
    get() = PhoneNumbers.display(phone)
}

/** 부르는 이름 규칙 — 앱이 정해 서버·동승자 웹에 그대로 보낸다(서버가 한국어 이름 규칙을 따로 갖지 않게). */
object CallNames {
  /** "이서연" → "서연", "김도윤" → "도윤". 성+이름 세 글자 한글일 때만 성을 뗀다. */
  fun of(name: String): String = if (name.length == 3 && name.all { it in '가'..'힣' }) name.substring(1) else name
}

object PhoneNumbers {
  /** "01000001234" → "010 - 0000 - 1234", "0212345678" → "02 - 1234 - 5678". 모르는 모양은 숫자 그대로. */
  fun display(phone: String): String {
    val d = phone.filter(Char::isDigit)
    val area = if (d.startsWith("02")) 2 else 3
    val rest = d.length - area
    if (rest !in 7..8) return d
    val mid = rest - 4
    return "${d.take(area)} - ${d.substring(area, area + mid)} - ${d.takeLast(4)}"
  }

  /** "01012341234" · "010-1234-1234" → "010-****-1234". 짧은 번호는 끝 4자리만 남긴다. */
  fun mask(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    if (digits.length < 9) return "****" + digits.takeLast(4)
    return "${digits.take(3)}-****-${digits.takeLast(4)}"
  }
}
