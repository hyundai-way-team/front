package com.kscho.way.server.zones

import com.kscho.way.server.zones.NoParkingText.Lookup
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 전국주정차금지(지정)구역표준데이터 한 줄 읽기 — 시간대·총연장·상세위치 글. */
class NoParkingRowsTest {
  private fun row(
    desc: String = "가람약국 앞 ~ 나무사거리",
    length: Double? = 300.0,
    weekday: DayRule = DayRule("08:00", "20:00"),
    saturday: DayRule = DayRule("00:00", "00:00"),
    holiday: DayRule = DayRule("00:00", "00:00"),
  ) = NoParkingRow("np-41280-1", "경기도", "고양시", "41280", listOf("가람로"), "가람로", desc, length, weekday, saturday, holiday)

  @Test
  fun minutes_readsClockText() {
    assertEquals(480, NoParkingSchedule.minutes("08:00"))
    assertEquals(540, NoParkingSchedule.minutes("9:00")) // 시 한 자리
    assertEquals(1439, NoParkingSchedule.minutes("2359"))
    assertEquals(1440, NoParkingSchedule.minutes("24:00"))
    assertNull(NoParkingSchedule.minutes("25:00"))
    assertNull(NoParkingSchedule.minutes("11:30+18:00"))
    assertNull(NoParkingSchedule.minutes(""))
  }

  @Test
  fun ranges_followTheStandardDataConventions() {
    // 00:00~00:00 = 그날 금지 없음, ~23:59 = 자정까지
    assertEquals(emptyList<Pair<Int, Int>>(), NoParkingSchedule.ranges(DayRule("00:00", "00:00")))
    assertEquals(listOf(0 to 1440), NoParkingSchedule.ranges(DayRule("00:00", "23:59")))
    // 밤을 넘기는 규제는 같은 날 두 창
    assertEquals(listOf(1320 to 1440, 0 to 420), NoParkingSchedule.ranges(DayRule("22:00", "07:00")))
    // 점심 잠깐 허용은 금지 창에서 뺀다
    assertEquals(listOf(480 to 690, 870 to 1110), NoParkingSchedule.ranges(DayRule("08:00", "18:30", "11:30", "14:30")))
  }

  @Test
  fun ranges_subtractEveryAllowance_joinedWithPlus_evenOvernight() {
    // 의왕시 모양: 09~18 금지, 허용 '11:30+18:00' ~ '14:00+09:00'
    assertEquals(listOf(540 to 690, 840 to 1080), NoParkingSchedule.ranges(DayRule("09:00", "18:00", "11:30+18:00", "14:00+09:00")))
    // 강북구 모양: 종일 금지, 허용 22~06 · 12~14 → 06~12, 14~22 만 금지
    assertEquals(listOf(360 to 720, 840 to 1320), NoParkingSchedule.ranges(DayRule("00:00", "23:59", "22:00+12:00", "06:00+14:00")))
    assertEquals(listOf(480 to 690, 870 to 1260), NoParkingSchedule.ranges(DayRule("8:00", "21:00", "11:30", "14:30")))
  }

  @Test
  fun windows_groupDaysWithTheSameHours_andHolidayIsSunday() {
    val w = NoParkingSchedule.windows(row(weekday = DayRule("09:00", "18:00"), saturday = DayRule("09:00", "18:00"), holiday = DayRule("10:00", "17:00")))

    assertEquals(2, w.size)
    assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), w[0].days)
    assertEquals(540 to 1080, w[0].startMinute to w[0].endMinute)
    assertEquals(setOf(DayOfWeek.SUNDAY), w[1].days)
    assertFalse(NoParkingSchedule.isAlways(w))
  }

  @Test
  fun windows_allDayEveryDay_isAlways_andNoHoursIsNothing() {
    val always = DayRule("00:00", "23:59")
    assertTrue(NoParkingSchedule.isAlways(NoParkingSchedule.windows(row(weekday = always, saturday = always, holiday = always))))
    val none = DayRule("00:00", "00:00")
    assertTrue(NoParkingSchedule.windows(row(weekday = none)).isEmpty())
  }

  @Test
  fun describe_readsLikeASign() {
    assertEquals("지정 주정차 금지 · 평일 08:00~20:00", NoParkingSchedule.describe(row()))
    assertEquals(
      "지정 주정차 금지 · 평일 08:00~18:30(11:30~14:30 제외) · 토·일·공휴일 11:00~18:00",
      NoParkingSchedule.describe(row(weekday = DayRule("08:00", "18:30", "11:30", "14:30"), saturday = DayRule("11:00", "18:00"), holiday = DayRule("11:00", "18:00"))),
    )
    assertEquals("지정 주정차 금지 · 평일 09:00~18:00(11:30~14:00, 18:00~09:00 제외)", NoParkingSchedule.describe(row(weekday = DayRule("09:00", "18:00", "11:30+18:00", "14:00+09:00"))))
    val always = DayRule("00:00", "23:59")
    assertEquals("지정 주정차 금지 · 상시", NoParkingSchedule.describe(row(weekday = always, saturday = always, holiday = always)))
  }

  @Test
  fun endpoints_splitOnRangeMarks_butKeepLotNumbersAndParentheses() {
    assertEquals(listOf("남지읍 남지리 남지약국앞 사거리", "대신사거리"), NoParkingText.endpoints("남지읍 남지리 남지약국앞 사거리 ∼ 대신사거리"))
    assertEquals(listOf("매탄3동 1175-13", "매탄3동 1174-11"), NoParkingText.endpoints("매탄3동 1175-13~매탄3동 1174-11"))
    assertEquals(listOf("아바이순대식당", "백석중개사 앞"), NoParkingText.endpoints("아바이순대식당-백석중개사 앞"))
    assertEquals(listOf("부천중사거리(심곡314-2)", "장말삼거리(심곡355-1)"), NoParkingText.endpoints("부천중사거리(심곡314-2) ↔ 장말삼거리(심곡355-1)"))
    assertEquals(listOf("진건오남로390번길"), NoParkingText.endpoints("진건오남로390번길(양측 0.85km)"))
    assertEquals(listOf("삼평동626-1"), NoParkingText.endpoints("삼평동626-1"))
    // 괄호 안의 ↔ 는 나누지 않는다 · ⇔ · 'A에서 B까지'
    assertEquals(listOf("군포초교일원(반석부동산↔마르빌(아))"), NoParkingText.endpoints("군포초교일원(반석부동산↔마르빌(아))"))
    assertEquals(listOf("가람사거리", "나무삼거리"), NoParkingText.endpoints("가람사거리 ⇔ 나무삼거리"))
    assertEquals(listOf("인하로 238", "인하로 300까지"), NoParkingText.endpoints("인하로 238에서 인하로 300까지"))
    // 영문 한 글자 뒤 '-' 는 나누지 않는다
    assertEquals(listOf("A-동 앞"), NoParkingText.endpoints("A-동 앞"))
  }

  @Test
  fun lookups_askAddressFirstWhenTheTextHasOne_thenThePlaceName() {
    val r = row().copy(sido = "경기도", sigungu = "수원시")
    assertEquals(listOf<Lookup>(Lookup.Address("경기도 수원시 매탄3동 1175-13")), NoParkingText.lookups("매탄3동 1175-13", r))
    assertEquals(listOf<Lookup>(Lookup.Keyword("남지약국앞 사거리"), Lookup.Keyword("남지약국")), NoParkingText.lookups("남지약국앞 사거리", r))
    val bupyeong = row().copy(sido = "인천광역시", sigungu = "부평구")
    assertEquals(
      listOf<Lookup>(Lookup.Address("인천광역시 부평구 부일로 39"), Lookup.Keyword("부일로 39 부개서초등학교")),
      NoParkingText.lookups("부일로 39 부개서초등학교 앞 도로", bupyeong),
    )
    assertEquals(listOf<Lookup>(Lookup.Address("인천광역시 부평구 부영로 166번길 12")), NoParkingText.lookups("부영로 166번길 12", bupyeong))
  }

  @Test
  fun lookups_numberOnlyEndpoint_borrowsThePreviousDong_orTheRoad() {
    val suwon = row().copy(sido = "경기도", sigungu = "수원시")
    assertEquals(listOf<Lookup>(Lookup.Address("경기도 수원시 매탄동 198-61")), NoParkingText.lookups("198-61", suwon, previous = "매탄동 198-71"))
    val songpa = row().copy(sido = "서울특별시", sigungu = "송파구", roads = listOf("동남로"))
    // 괄호 안 이름은 키워드로도
    assertEquals(listOf<Lookup>(Lookup.Address("서울특별시 송파구 동남로 187"), Lookup.Keyword("잠실교회")), NoParkingText.lookups("187(잠실교회 주변)", songpa))
    // 지번 꼴('심곡314-2')은 키워드로 묻지 않는다
    assertEquals(listOf<Lookup>(Lookup.Keyword("부천중사거리")), NoParkingText.lookups("부천중사거리(심곡314-2)", suwon))
  }

  @Test
  fun parse_readsToolCsv_withQuotedCommas_andMetres() {
    val csv =
      """
      id,roads,sido,sigungu,sigungu_code,road_raw,desc,length,wk_open,wk_close,wk_allow_from,wk_allow_to,sat_open,sat_close,sat_allow_from,sat_allow_to,hol_open,hol_close,hol_allow_from,hol_allow_to,length_m
      np-41280-1,황무로;마소로,경기도,고양시,41280,"황무로,마소로","가람약국 앞, 나무사거리",0.12,08:00,20:00,,,00:00,00:00,,,00:00,00:00,,,120
      np-41570-2,걸포로,경기도,김포시,41570,걸포로,걸포동 1,1,08:00,20:00,,,00:00,00:00,,,00:00,00:00,,,
      짧은,줄
      """.trimIndent()

    val rows = NoParkingRow.parse(csv.byteInputStream())

    assertEquals(2, rows.size)
    val r = rows.first()
    assertEquals(listOf("황무로", "마소로"), r.roads)
    assertEquals("가람약국 앞, 나무사거리", r.desc)
    assertEquals(120.0, r.lengthMeters!!, 0.0)
    assertEquals(DayRule("08:00", "20:00"), r.weekday)
    assertNull(rows[1].lengthMeters) // 도구가 모르는 길이는 비워 둔다
  }

  @Test
  fun bundledCapitalData_isLoaded() {
    val rows = NoParkingRow.bundled()
    assertTrue(rows.size > 5_000)
    assertTrue(rows.all { it.id.startsWith("np-") && it.roads.isNotEmpty() })
    assertTrue(rows.count { it.lengthMeters != null } > 5_000)
  }
}
