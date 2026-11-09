package com.kscho.way.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** 화면 글자 모양. 서비스가 한국 전용이라 시간대는 서울로 고정(에뮬레이터 시간대와 무관하게). */
object Format {
  private val zone = ZoneId.of("Asia/Seoul")
  private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

  fun clock(epochMillis: Long): String = hhmm.format(Instant.ofEpochMilli(epochMillis))

  /** 남은 시간 "12분" (1분 미만은 "1분"). */
  fun minutes(seconds: Int): String = "${ceil(seconds / 60.0).toInt().coerceAtLeast(1)}분"

  /** "약 40분" · "약 1시간 5분" */
  fun duration(seconds: Int): String {
    val total = ceil(seconds / 60.0).toInt().coerceAtLeast(1)
    return if (total < 60) "약 ${total}분" else "약 ${total / 60}시간" + if (total % 60 > 0) " ${total % 60}분" else ""
  }

  fun distance(meters: Int): String = if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters}m"

  /** 대기 타이머 "0:42" */
  fun timer(millis: Long): String {
    val s = ceil(millis / 1000.0).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
  }

  /** 지난 날짜 "오늘" · "어제" · "3일 전" */
  fun ago(epochMillis: Long, nowMillis: Long): String {
    val days = ((nowMillis - epochMillis) / (24 * 60 * 60 * 1000L)).toInt()
    return when {
      days <= 0 -> "오늘"
      days == 1 -> "어제"
      else -> "${days}일 전"
    }
  }
}
