package com.kscho.way.data.repository

import com.kscho.way.data.dataSource.local.contact.ContactLocalDataSource
import com.kscho.way.data.dataSource.local.contact.DeviceContact
import com.kscho.way.data.dataSource.remote.StopZoneRemoteDataSourceImpl
import com.kscho.way.data.fake.FakeDeviceContacts
import com.kscho.way.data.fake.FakeWayApi
import com.kscho.way.data.fake.FakeWorld
import com.kscho.way.data.repository.contact.ContactRepositoryImpl
import com.kscho.way.data.repository.restriction.StopRestrictionRepositoryImpl
import com.kscho.way.domain.model.base.getOrThrow
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.pickup.StopRestrictionRules
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {
  private class FakeHistory(private val records: List<PickupRecord> = emptyList()) : PickupHistoryRepository {
    override suspend fun recent(limit: Int): List<PickupRecord> = records.take(limit)

    override suspend fun save(record: PickupRecord) = Unit
  }

  private fun record(passenger: Contact) = FakeWorld.demoHistory(0).first().copy(passenger = passenger)

  @Test
  fun contacts_searchByNameOrPhoneDigits() = runTest {
    val repo = ContactRepositoryImpl(FakeDeviceContacts(), FakeHistory())
    assertEquals(5, repo.search("").size)
    assertEquals("이서연", repo.search("서연").single().name)
    assertEquals("이서연", repo.search("1234").single().name)
    assertTrue(repo.search("없는사람").isEmpty())
  }

  @Test
  fun contacts_recentPassengersComeFirstWithoutDuplicates() = runTest {
    val device = object : ContactLocalDataSource {
      override suspend fun all() =
        listOf(DeviceContact("d1", "김민준", "010-0000-5678"), DeviceContact("d2", "이서연", "010-0000-1234"), DeviceContact("d3", "박지호", "01000009012"))
    }
    val repo = ContactRepositoryImpl(device, FakeHistory(listOf(record(Contact("h1", "이서연", "01000001234")), record(Contact("h2", "정하준", "01000007890")))))

    val all = repo.search("")
    assertEquals(listOf("이서연", "정하준", "김민준", "박지호"), all.map { it.name })
    assertEquals("d2", all.first().id)
    assertEquals(listOf("정하준"), repo.search("하준").map { it.name })
  }

  @Test
  fun restriction_demoNeighborhood_judgedByAppRules() = runTest {
    val repo = StopRestrictionRepositoryImpl(StopZoneRemoteDataSourceImpl(FakeWayApi(backgroundScope)))
    fun judge(at: LatLng, time: String) =
      StopRestrictionRules.check(at, LocalDateTime.parse(time).atZone(StopRestrictionRules.KOREA).toInstant().toEpochMilli(), runBlocking { repo.zonesNear(at, 100).getOrThrow() })

    val bus = judge(FakeWorld.BUS_STOP, "2026-11-14T23:30:00")
    assertTrue(bus is StopRestriction.NoStopping)
    assertEquals("절대 주정차 금지 · 상시", (bus as StopRestriction.NoStopping).zone.rule)
    assertEquals(StopRestriction.NotFound, judge(FakeWorld.VILLA_ALLEY_ENTRANCE, "2026-11-09T09:50:00"))
    val curb = Geo.offset(LatLng(FakeWorld.latitudeAt(FakeWorld.SINCHON_RO_SOUTH, 126.944)!!, 126.944), 0.0, -8.0)
    assertTrue(judge(curb, "2026-11-09T09:50:00") is StopRestriction.NoStopping)
    assertEquals(StopRestriction.NotFound, judge(curb, "2026-11-09T22:30:00"))
    assertEquals(StopRestriction.NotFound, judge(curb, "2026-11-14T09:50:00"))
  }
}
