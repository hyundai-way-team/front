package com.kscho.way.data.repository

import com.kscho.way.data.contact.ContactDataSource
import com.kscho.way.data.mock.MockPleosContacts
import com.kscho.way.data.mock.MockWayApi
import com.kscho.way.data.mock.MockWorld
import com.kscho.way.data.remote.ContactDto
import com.kscho.way.domain.model.base.getOrThrow
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.pickup.StopRestrictionRules
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
  fun contacts_recentPassengersComeFirstWithoutDuplicates() = runTest {
    val device = object : ContactDataSource {
      override suspend fun all() =
        listOf(ContactDto("d1", "김민준", "010-0000-5678"), ContactDto("d2", "이서연", "010-0000-1234"), ContactDto("d3", "박지호", "01000009012"))
    }
    // 지난 픽업 동승자: 이서연(연락처에도 있음 — 번호 모양이 달라도 같은 사람) · 정하준(연락처에서 지워짐)
    val repo = ContactRepositoryImpl(device, recent = { listOf(Contact("h1", "이서연", "01000001234"), Contact("h2", "정하준", "01000007890")) })

    val all = repo.search("")
    assertEquals(listOf("이서연", "정하준", "김민준", "박지호"), all.map { it.name })
    assertEquals("d2", all.first().id) // 연락처에 있으면 연락처 쪽 항목을 쓴다
    assertEquals(listOf("정하준"), repo.search("하준").map { it.name })
  }

  @Test
  fun restriction_demoNeighborhood_judgedByAppRules() = runTest {
    val repo = StopRestrictionRepositoryImpl(MockWayApi(backgroundScope))
    fun judge(at: LatLng, time: String) =
      StopRestrictionRules.check(at, LocalDateTime.parse(time).atZone(StopRestrictionRules.KOREA).toInstant().toEpochMilli(), runBlocking { repo.zonesNear(at, 100).getOrThrow() })

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
