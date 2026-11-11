package com.kscho.way.data.repository

import com.kscho.way.data.mock.MockPleosContacts
import com.kscho.way.data.mock.MockWayApi
import com.kscho.way.data.mock.MockWorld
import com.kscho.way.domain.model.StopRestriction
import kotlinx.coroutines.test.runTest
import com.kscho.way.domain.usecase.StopRestrictionRules
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {
  @Test
  fun contacts_searchByNameOrPhoneDigits() = runTest {
    val repo = ContactRepositoryImpl(MockPleosContacts())
    assertEquals(5, repo.search("").size)
    assertEquals("이서연", repo.search("서연").single().name)
    assertEquals("이서연", repo.search("1234").single().name)
    assertTrue(repo.search("없는사람").isEmpty())
  }

  @Test
  fun restriction_demoNeighborhood_judgedByAppRules() = runTest {
    val repo = StopRestrictionRepositoryImpl(MockWayApi(backgroundScope))
    fun judge(at: LatLng, time: String) =
      StopRestrictionRules.check(at, LocalDateTime.parse(time).atZone(StopRestrictionRules.KOREA).toInstant().toEpochMilli(), runBlocking { repo.zonesNear(at, 100) })

    val bus = judge(MockWorld.BUS_STOP, "2026-11-14T23:30:00")
    assertTrue(bus is StopRestriction.NoStopping)
    assertEquals("절대 주정차 금지 · 상시", (bus as StopRestriction.NoStopping).zone.rule)
    assertEquals(StopRestriction.NotFound, judge(MockWorld.VILLA_ALLEY_ENTRANCE, "2026-11-09T09:50:00"))
    // 신촌로 남쪽 차로에서 8m 바깥 길가 — 평일 07~22시만
    val curb = Geo.offset(LatLng(MockWorld.latitudeAt(MockWorld.SINCHON_RO_SOUTH, 126.944)!!, 126.944), 0.0, -8.0)
    assertTrue(judge(curb, "2026-11-09T09:50:00") is StopRestriction.NoStopping) // 월 09:50
    assertEquals(StopRestriction.NotFound, judge(curb, "2026-11-09T22:30:00")) // 월 22:30
    assertEquals(StopRestriction.NotFound, judge(curb, "2026-11-14T09:50:00")) // 토
  }
}
