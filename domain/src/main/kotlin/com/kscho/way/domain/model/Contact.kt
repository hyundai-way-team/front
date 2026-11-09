package com.kscho.way.domain.model

/** 픽업할 사람(MVP: 동승자 1명). 전화번호는 차량 밖 안내(Gleo 응답 등)에 넣지 않는다. */
data class Contact(val id: String, val name: String, val phone: String) {
  /** 화면·음성에서 부르는 이름. "이서연" → "서연" (성+이름 세 글자 한글일 때만 성을 뗀다). */
  val callName: String
    get() = if (name.length == 3 && name.all { it in '가'..'힣' }) name.substring(1) else name

  val maskedPhone: String
    get() = PhoneNumbers.mask(phone)
}

object PhoneNumbers {
  /** "01012341234" · "010-1234-1234" → "010-****-1234". 짧은 번호는 끝 4자리만 남긴다. */
  fun mask(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    if (digits.length < 9) return "****" + digits.takeLast(4)
    return "${digits.take(3)}-****-${digits.takeLast(4)}"
  }
}
