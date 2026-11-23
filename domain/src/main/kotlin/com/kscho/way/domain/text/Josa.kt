package com.kscho.way.domain.text

object Josa {
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
      last.isDigit() -> last in "036"
      else -> last.lowercaseChar() in "mn" || letters.lowercase().endsWith("ng")
    }
  }

  private const val RIEUL = 8
}
