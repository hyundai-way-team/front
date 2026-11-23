package com.kscho.way.server.zones

import com.kscho.way.data.response.StopZoneResponse
import java.io.InputStream
import java.time.DayOfWeek
import java.util.zip.GZIPInputStream

/**
 * 하루 규칙(표준데이터 칸 그대로) — 금지 시작·끝, 그 사이 잠깐 허용(예: 점심 11:30~14:30).
 * 허용 칸은 '11:30+18:00' / '14:00+09:00' 처럼 '+' 로 여러 창을 잇기도 한다(앞은 앞끼리, 뒤는 뒤끼리 짝).
 */
data class DayRule(val open: String, val close: String, val allowFrom: String = "", val allowTo: String = "")

/**
 * 지정 주정차 금지구역 한 곳 — 전국주정차금지(지정)구역표준데이터(공공데이터포털 15034542) 한 줄. 좌표가 없다.
 * [roads] 는 도구가 정리한 도로 이름(공백 없음, 건물번호 뗌), [desc] 는 '○○약국 앞 ∼ ○○사거리' 같은 상세위치 글.
 * [lengthMeters] 는 총연장(m) — 기관마다 km·m 로 섞어 적은 것을 도구가 기관 단위로 m 로 바꿔 둔다(모르면 null).
 */
data class NoParkingRow(
  val id: String,
  val sido: String,
  val sigungu: String,
  val sigunguCode: String,
  val roads: List<String>,
  val roadRaw: String,
  val desc: String,
  val lengthMeters: Double?,
  val weekday: DayRule,
  val saturday: DayRule,
  val holiday: DayRule,
) {
  companion object {
    private const val RESOURCE = "/data/no_parking_capital.csv.gz"

    /** tools/data/build_no_parking_zones.py 가 쓴 CSV(머리글 있음). 칸이 모자란 줄은 건너뛴다. */
    fun parse(input: InputStream): List<NoParkingRow> =
      input.bufferedReader(Charsets.UTF_8).useLines { lines ->
        val it = lines.iterator()
        if (!it.hasNext()) return@useLines emptyList()
        val head = Csv.split(it.next()).withIndex().associate { (i, name) -> name to i }
        val out = ArrayList<NoParkingRow>()
        while (it.hasNext()) {
          val p = Csv.split(it.next())
          if (p.size < head.size) continue
          fun col(name: String) = head[name]?.let { i -> p.getOrNull(i) }?.trim().orEmpty()
          fun day(prefix: String) = DayRule(col("${prefix}_open"), col("${prefix}_close"), col("${prefix}_allow_from"), col("${prefix}_allow_to"))
          out +=
            NoParkingRow(
              id = col("id"),
              sido = col("sido"),
              sigungu = col("sigungu"),
              sigunguCode = col("sigungu_code"),
              roads = col("roads").split(';').filter { r -> r.isNotBlank() },
              roadRaw = col("road_raw"),
              desc = col("desc"),
              lengthMeters = col("length_m").toDoubleOrNull()?.takeIf { m -> m > 0.0 },
              weekday = day("wk"),
              saturday = day("sat"),
              holiday = day("hol"),
            )
        }
        out
      }

    fun bundled(): List<NoParkingRow> {
      val stream = NoParkingRow::class.java.getResourceAsStream(RESOURCE) ?: error("지정 주정차 금지구역 데이터가 없어요: $RESOURCE")
      return GZIPInputStream(stream).use { parse(it) }
    }
  }
}

/**
 * 표준데이터의 요일별 시각 → 앱 판정용 시간 창(StopZoneResponse.days·startMinute·endMinute).
 * - '00:00 ~ 00:00' 은 그날 금지 없음, '~ 23:59' 는 자정까지, 시작이 끝보다 늦으면 밤을 넘기는 규제(같은 날 두 창으로).
 * - 잠깐 허용 시간은 금지 창에서 뺀다(여러 개, 밤을 넘기는 것도).
 * - 공휴일 칸은 일요일로 쓴다(공휴일 달력은 아직 없다).
 */
object NoParkingSchedule {
  data class Window(val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int)

  private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
  private const val DAY = 24 * 60
  private val CLOCK = Regex("^(\\d{1,2}):?(\\d{2})$")

  /** 빈 목록 = 금지 시간이 하나도 없음. 모든 요일 하루 종일이면 [isAlways]. */
  fun windows(row: NoParkingRow): List<Window> {
    val groups = listOf(WEEKDAYS to row.weekday, setOf(DayOfWeek.SATURDAY) to row.saturday, setOf(DayOfWeek.SUNDAY) to row.holiday)
    val byRange = LinkedHashMap<Pair<Int, Int>, MutableSet<DayOfWeek>>()
    for ((days, rule) in groups) {
      for (range in ranges(rule)) byRange.getOrPut(range) { mutableSetOf() } += days
    }
    return byRange.map { (r, days) -> Window(days.toSortedSet(), r.first, r.second) }
  }

  fun isAlways(windows: List<Window>): Boolean = windows.size == 1 && windows[0].days.size == 7 && windows[0].startMinute == 0 && windows[0].endMinute == DAY

  /** 하루 규칙 → 금지 구간들(분, 끝은 제외). */
  fun ranges(rule: DayRule): List<Pair<Int, Int>> {
    var out = span(rule.open, rule.close) ?: return emptyList()
    for (allow in allowances(rule)) out = out.flatMap { (s, e) -> listOf(s to minOf(e, allow.first), maxOf(s, allow.second) to e).filter { it.first < it.second } }
    return out
  }

  /** 잠깐 허용 창들(분). 밤을 넘기면 두 조각. */
  fun allowances(rule: DayRule): List<Pair<Int, Int>> {
    val froms = rule.allowFrom.split('+').map { it.trim() }
    val tos = rule.allowTo.split('+').map { it.trim() }
    return froms.zip(tos).flatMap { (f, t) -> span(f, t).orEmpty() }
  }

  /** 시작·끝 글 → 구간들. 같으면(00:00~00:00) 없음, 23:59 는 자정, 시작 > 끝이면 밤을 넘김. 못 읽으면 null. */
  private fun span(from: String, to: String): List<Pair<Int, Int>>? {
    val open = minutes(from) ?: return null
    var close = minutes(to) ?: return null
    if (open == close) return emptyList()
    if (close == DAY - 1) close = DAY
    return if (open < close) listOf(open to close) else listOf(open to DAY, 0 to close).filter { it.first < it.second }
  }

  /** 'HH:MM' · 'H:MM' · 'HHMM' → 분. 24:00 은 하루 끝. */
  fun minutes(text: String): Int? {
    val m = CLOCK.matchEntire(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (min > 59 || h > 24 || (h == 24 && min != 0)) return null
    return h * 60 + min
  }

  /** 사람이 읽는 규칙 — "지정 주정차 금지 · 평일 08:00~21:00(11:30~14:30 제외) · 토·일·공휴일 11:00~18:00". */
  fun describe(row: NoParkingRow): String {
    val parts =
      listOf("평일" to row.weekday, "토" to row.saturday, "일·공휴일" to row.holiday)
        .mapNotNull { (label, rule) -> text(rule)?.let { label to it } }
    if (parts.isEmpty()) return "지정 주정차 금지"
    if (parts.size == 3 && parts.all { it.second == "종일" }) return "지정 주정차 금지 · 상시"
    val merged = ArrayList<Pair<String, String>>()
    for (p in parts) {
      val last = merged.lastOrNull()
      if (last != null && last.second == p.second) merged[merged.lastIndex] = "${last.first}·${p.first}" to p.second else merged += p
    }
    return "지정 주정차 금지 · " + merged.joinToString(" · ") { "${it.first} ${it.second}" }
  }

  private fun text(rule: DayRule): String? {
    val ranges = ranges(rule)
    if (ranges.isEmpty()) return null
    val open = minutes(rule.open) ?: return null
    val close = minutes(rule.close) ?: return null
    val main = if (open == 0 && close >= DAY - 1) "종일" else "${hhmm(open)}~${hhmm(close)}"
    val allowed =
      rule.allowFrom.split('+').zip(rule.allowTo.split('+')).mapNotNull { (f, t) ->
        val a = minutes(f) ?: return@mapNotNull null
        val b = minutes(t) ?: return@mapNotNull null
        if (a == b) null else "${hhmm(a)}~${hhmm(b)}"
      }
    return if (allowed.isEmpty()) main else "$main(${allowed.joinToString(", ")} 제외)"
  }

  private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
}

/** 상세위치 글 → 끝점 글('○○약국 앞 ∼ ○○사거리' → 둘) → 카카오에 물을 말. */
object NoParkingText {
  sealed interface Lookup {
    val query: String

    /** 지번·도로명 주소(주소 검색). */
    data class Address(override val query: String) : Lookup

    /** 가게·교차로 이름(키워드 검색). */
    data class Keyword(override val query: String) : Lookup
  }

  // 구간 표시: ~ ∼ 〜 ～ ↔ ⇔ → (괄호 밖에서만). '-' 는 한글 사이에서만(지번 '1175-13'·영문 한 글자 'A-동'은 그대로)
  private val STRONG = setOf('~', '∼', '〜', '～', '↔', '⇔', '→')
  private val DASH = Regex("(?<=[가-힣)\\]])\\s*[-–—]\\s*(?=[가-힣(\\[])")
  private val LOT = Regex("([가-힣0-9]+(?:동|리|가))\\s*(\\d+(?:-\\d+)?)(?:번지)?(?:\\s*(\\d+)호)?")
  private val ROAD_NO = Regex("([가-힣0-9]+(?:대로|로|길)(?:\\s*\\d+번길)?)\\s*(\\d+(?:-\\d+)?)(?![가-힣0-9])")
  private val NUMBER_ONLY = Regex("^(\\d+(?:-\\d+)?)(?:번지)?$")
  private val TAIL = Regex("(\\s*(앞|앞쪽|부근|주변|맞은편|건너편|일원|일대|인근|옆|뒤|쪽|방향|사이|구간|까지|부터|에서|도로|출입구|입구|정문|후문))+$")
  private val PARENS = Regex("\\(([^)]*)\\)")
  private val LOT_LIKE = Regex("\\d+-\\d+")

  fun endpoints(desc: String): List<String> {
    var s = desc.replace(Regex("\\([^)]*km[^)]*\\)"), " ")
    // 'A에서 B까지'
    if (s.contains("까지") && s.contains("에서 ")) s = s.replaceFirst("에서 ", " ~ ")
    return splitOutsideParens(s).flatMap { it.split(DASH) }.map { it.trim() }.filter { it.isNotBlank() }
  }

  private fun splitOutsideParens(s: String): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var depth = 0
    for (ch in s) {
      when {
        ch == '(' || ch == '[' -> depth++
        (ch == ')' || ch == ']') && depth > 0 -> depth--
      }
      if (depth == 0 && ch in STRONG) {
        out += cur.toString()
        cur.setLength(0)
      } else {
        cur.append(ch)
      }
    }
    out += cur.toString()
    return out
  }

  /**
   * 끝점 글 하나 → 물어볼 순서(주소가 보이면 주소 먼저, 다음 이름). [row] 의 시도·시군구를 주소 앞에 붙인다.
   * 번지만 있는 끝점('198-61')은 [previous] 끝점의 동을 물려받고, 그것도 없으면 도로명 + 번호('동남로 187')로 묻는다.
   */
  fun lookups(text: String, row: NoParkingRow, previous: String? = null): List<Lookup> {
    val out = ArrayList<Lookup>()
    val place = "${row.sido} ${row.sigungu.substringBefore(' ')}"
    val inner = PARENS.findAll(text).map { it.groupValues[1].trim() }.toList()
    val outer = text.replace(PARENS, " ").replace(Regex("\\s+"), " ").trim()
    NUMBER_ONLY.matchEntire(outer)?.let { m ->
      val dong = previous?.let { LOT.find(it)?.groupValues?.get(1) }
      out += if (dong != null) Lookup.Address("$place $dong ${m.groupValues[1]}") else Lookup.Address("$place ${row.roads.firstOrNull().orEmpty()} ${m.groupValues[1]}")
    }
    for (s in listOf(outer) + inner) {
      LOT.find(s)?.let { m -> out += Lookup.Address("$place ${m.groupValues[1]} ${m.groupValues[2]}") }
      ROAD_NO.find(s)?.let { m -> out += Lookup.Address("$place ${m.groupValues[1]} ${m.groupValues[2]}") }
    }
    for (s in listOf(outer) + inner) {
      val name = s.replace(TAIL, "").replace(LOT, " ").replace(Regex("\\s+"), " ").trim()
      // 도로명주소뿐('부영로 166번길 12')이면 이미 주소로 물었다
      if (name.replace(ROAD_NO, " ").isBlank()) continue
      // '남지약국앞 사거리' 처럼 '앞' 뒤에 말이 더 붙은 것은 '앞' 앞쪽 이름도 물어본다
      val before = name.substringBefore("앞", "").trim()
      for (q in listOf(name, before)) {
        // 숫자뿐이거나 지번 꼴('심곡314-2')은 이름이 아니다 — 주소로 이미 물었다
        if (q.length >= 2 && !q.all { it.isDigit() || it == '-' || it == ' ' } && !LOT_LIKE.containsMatchIn(q)) out += Lookup.Keyword(q)
      }
    }
    return out.distinct()
  }
}

/** 따옴표로 감싼 칸(쉼표 든 상세위치)까지 읽는 짧은 CSV 나누기 — BusStopZones 와 같은 규칙. */
internal object Csv {
  fun split(line: String): List<String> {
    val out = ArrayList<String>(32)
    val cur = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
      val ch = line[i]
      when {
        ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
          cur.append('"')
          i++
        }
        ch == '"' -> quoted = !quoted
        ch == ',' && !quoted -> {
          out += cur.toString()
          cur.setLength(0)
        }
        else -> cur.append(ch)
      }
      i++
    }
    out += cur.toString()
    return out
  }
}
