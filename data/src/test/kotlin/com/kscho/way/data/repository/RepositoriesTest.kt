package com.kscho.way.data.repository

import com.kscho.way.data.mock.MockPleosContacts
import com.kscho.way.data.mock.MockWayApi
import com.kscho.way.data.mock.MockWorld
import com.kscho.way.domain.model.StopRestriction
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
  fun restriction_mapsZone() = runTest {
    val repo = StopRestrictionRepositoryImpl(MockWayApi(backgroundScope))
    val result = repo.check(MockWorld.BUS_STOP, 0L)
    assertTrue(result is StopRestriction.NoStopping)
    assertEquals("절대 주정차 금지 · 상시", (result as StopRestriction.NoStopping).zone.rule)
  }

  @Test
  fun history_mapsSeededRecord() = runTest {
    val first = PickupHistoryRepositoryImpl(MockWayApi(backgroundScope)).recent(5).first()
    assertEquals("이서연", first.passenger.name)
    assertEquals("010-****-1234", first.passenger.maskedPhone)
    assertEquals(MockWorld.VILLA_ALLEY_ENTRANCE, first.pickupPoint.location)
    assertEquals("신촌 XX 빌라", first.place?.name)
  }
}
