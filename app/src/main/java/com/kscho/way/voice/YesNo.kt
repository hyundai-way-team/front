package com.kscho.way.voice

enum class YesNo { YES, NO, UNCLEAR }

object YesNoParser {
  private val NO_PARTS =
    listOf("아니", "아뇨", "괜찮", "됐어", "됐습니다", "싫어", "나중에", "하지마", "하지말", "걸지마", "걸지말", "안걸", "안해", "필요없", "그만", "취소")

  private val YES_PARTS = listOf("전화해", "전화걸", "걸어", "해줘", "해주세요", "부탁")

  private val YES_WORDS = setOf("응", "어", "네", "예", "웅", "엉", "그래", "좋아", "오케이", "콜", "ok", "okay", "yes")

  private const val YES_SYLLABLES = "응어네예웅엉"

  fun parse(heard: String): YesNo {
    val lower = heard.lowercase()
    val compact = lower.filter { it.isLetterOrDigit() }
    if (compact.isEmpty()) return YesNo.UNCLEAR
    if (NO_PARTS.any { it in compact }) return YesNo.NO
    if (YES_PARTS.any { it in compact }) return YesNo.YES
    val words = lower.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    return if (words.any(::isYesWord)) YesNo.YES else YesNo.UNCLEAR
  }

  private fun isYesWord(word: String): Boolean {
    val w = word.removeSuffix("요")
    return w in YES_WORDS || (w.isNotEmpty() && w.all { it in YES_SYLLABLES })
  }
}
