package com.kscho.way.presentation.component.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

object Format {
  private val zone = ZoneId.of("Asia/Seoul")
  private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

  fun clock(epochMillis: Long): String = hhmm.format(Instant.ofEpochMilli(epochMillis))

  fun minutes(seconds: Int): String = "${ceil(seconds / 60.0).toInt().coerceAtLeast(1)}분"

  fun duration(seconds: Int): String {
    val total = ceil(seconds / 60.0).toInt().coerceAtLeast(1)
    return if (total < 60) "약 ${total}분" else "약 ${total / 60}시간" + if (total % 60 > 0) " ${total % 60}분" else ""
  }

  fun distance(meters: Int): String = if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters}m"

  fun timer(millis: Long): String {
    val s = ceil(millis / 1000.0).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
  }

  fun ago(epochMillis: Long, nowMillis: Long): String {
    val days = ((nowMillis - epochMillis) / (24 * 60 * 60 * 1000L)).toInt()
    return when {
      days <= 0 -> "오늘"
      days == 1 -> "어제"
      else -> "${days}일 전"
    }
  }
}
