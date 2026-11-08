package com.kscho.way.voice

/** 운전자가 '전화를 걸까요?' 같은 질문에 한 대답. */
enum class YesNo { YES, NO, UNCLEAR }

/**
 * 온디바이스 STT 가 돌려준 한국어 문장을 예/아니오로 읽는다.
 * STT 는 '응 전화해줘'처럼 문장부호 없이, 띄어쓰기는 들쭉날쭉하게 준다.
 * 부정('전화하지 마')을 먼저 본다 — 잘못 걸리는 전화보다 안 걸리는 쪽이 덜 위험하다.
 */
object YesNoParser {
  /** 띄어쓰기를 지운 문장에 들어 있으면 '아니오'. */
  private val NO_PARTS =
    listOf("아니", "아뇨", "괜찮", "됐어", "됐습니다", "싫어", "나중에", "하지마", "하지말", "걸지마", "걸지말", "안걸", "안해", "필요없", "그만", "취소")

  /** 띄어쓰기를 지운 문장에 들어 있으면 '예'. */
  private val YES_PARTS = listOf("전화해", "전화걸", "걸어", "해줘", "해주세요", "부탁")

  /** 낱말 하나가 통째로 이것이면 '예'. 한 글자 대답이 '어디야' 같은 낱말 속에서 잡히지 않게 낱말 단위로 본다. */
  private val YES_WORDS = setOf("응", "어", "네", "예", "웅", "엉", "그래", "좋아", "오케이", "콜", "ok", "okay", "yes")

  /** '응응', '네네'처럼 같은 대답을 겹쳐 말한 낱말. */
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
