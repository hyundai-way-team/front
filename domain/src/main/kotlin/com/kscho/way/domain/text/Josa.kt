package com.kscho.way.domain.text

/**
 * 이름 뒤 조사 — 음성 안내 "5초 뒤 연남동 OO 식당으로 안내를 시작할게요".
 * 마지막으로 읽히는 글자(괄호·문장부호는 건너뜀)의 받침으로 고른다. 영어·숫자는 흔히 읽는 소리로 어림한다.
 */
object Josa {
  /** '으로/로' — 받침이 없거나 ㄹ 받침이면 '로'. */
  fun ro(word: String): String {
    if (word.isEmpty()) return word
    return word + if (needsEu(word)) "으로" else "로"
  }

  private fun needsEu(word: String): Boolean {
    val letters = word.filter { it.isLetterOrDigit() }
    val last = letters.lastOrNull() ?: return false
    return when {
      last in '가'..'힣' -> {
        val jong = (last - '가') % 28
        jong != 0 && jong != RIEUL
      }
      // 영 삼 육 은 받침(ㄹ 아님), 일 칠 팔 은 ㄹ, 나머지는 받침 없음
      last.isDigit() -> last in "036"
      // 끝이 m·n·ng 면 ㅁ·ㄴ·ㅇ 받침으로 읽힌다(Museum, Station, Bundang). l 은 ㄹ, 그 밖은 '스·크·트'처럼 받침 없이
      else -> last.lowercaseChar() in "mn" || letters.lowercase().endsWith("ng")
    }
  }

  private const val RIEUL = 8
}
