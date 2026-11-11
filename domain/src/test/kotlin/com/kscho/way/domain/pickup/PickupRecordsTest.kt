package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.PickupOutcome
import com.kscho.way.domain.model.PickupRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/** 홈 '최근 픽업'은 앱에 둔다 — 기록 만들기·같은 픽업 합치기. */
class PickupRecordsTest {
  private val outcome = PickupOutcome(boarded = true, boardedAtMillis = 900L, waitedSeconds = 38, pickupLocationChanges = 0, boardingSource = BoardingSource.DOOR)

  private fun record(id: String, passenger: Contact = Fixtures.seoyeon, title: String = "신촌 XX 빌라 골목 입구 앞") =
    PickupRecords.of(Fixtures.state().copy(id = id), outcome, endedAtMillis = 1_000L).let {
      it.copy(passenger = passenger, pickupPoint = it.pickupPoint.copy(description = LocationDescription(title, "신촌로 · 도로 북쪽 인도")))
    }

  @Test
  fun of_keepsWhoAndWhereFromPlan() {
    val r = PickupRecords.of(Fixtures.state(), outcome, endedAtMillis = 1_000L)
    assertEquals("p1", r.id)
    assertEquals(Fixtures.seoyeon, r.passenger)
    assertEquals(Fixtures.plan().pickupPoint, r.pickupPoint)
    assertEquals(38, r.waitedSeconds)
    assertEquals(1_000L, r.endedAtMillis)
  }

  @Test
  fun add_samePersonSamePlace_keepsOnlyNewest() {
    val old = record("old")
    val other = record("other", passenger = Contact("c2", "김민준", "01000005678"))
    val elsewhere = record("elsewhere", title = "현대백화점 신촌점 정문 앞")
    // 번호 모양이 달라도 같은 사람
    val again = record("again", passenger = Fixtures.seoyeon.copy(phone = "010-0000-1234"))

    val result = PickupRecords.add(listOf(old, other, elsewhere), again)

    assertEquals(listOf("again", "other", "elsewhere"), result.map(PickupRecord::id))
  }

  @Test
  fun add_keepsAtMostMax() {
    val many = (1..5).map { record("r$it", title = "장소 $it") }
    assertEquals(listOf("new", "r1", "r2"), PickupRecords.add(many, record("new"), max = 3).map(PickupRecord::id))
  }
}
