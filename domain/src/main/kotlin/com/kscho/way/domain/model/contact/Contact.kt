package com.kscho.way.domain.model.contact

data class Contact(val id: String, val name: String, val phone: String) {
  val callName: String
    get() = CallNames.of(name)

  val maskedPhone: String
    get() = PhoneNumbers.mask(phone)

  val displayPhone: String
    get() = PhoneNumbers.display(phone)

  val displayMaskedPhone: String
    get() = PhoneNumbers.displayMasked(phone)
}

object CallNames {
  fun of(name: String): String = if (name.length == 3 && name.all { it in '가'..'힣' }) name.substring(1) else name
}

object PhoneNumbers {
  fun display(phone: String): String {
    val d = phone.filter(Char::isDigit)
    val area = if (d.startsWith("02")) 2 else 3
    val rest = d.length - area
    if (rest !in 7..8) return d
    val mid = rest - 4
    return "${d.take(area)} - ${d.substring(area, area + mid)} - ${d.takeLast(4)}"
  }

  fun displayMasked(phone: String): String {
    val d = phone.filter(Char::isDigit)
    val area = if (d.startsWith("02")) 2 else 3
    if (d.length - area !in 7..8) return "****" + d.takeLast(4)
    return "${d.take(area)} - **** - ${d.takeLast(4)}"
  }

  fun mask(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    if (digits.length < 9) return "****" + digits.takeLast(4)
    return "${digits.take(3)}-****-${digits.takeLast(4)}"
  }
}
