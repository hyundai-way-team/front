package com.kscho.way.pickup

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupSessionStoreTest {
  private val session =
    PickupSession(
      passengerName = "이서연",
      passengerPhone = "01012345678",
      pickupPlaceName = "역삼역 3번 출구",
      remainingSeconds = 300,
      passengerCheckedLink = false,
      passengerSharingLocation = false,
    )

  @After fun tearDown() = PickupSessionStore.clear()

  @Test
  fun markBoarded_withoutSession_returnsNullAndStaysEmpty() {
    assertNull(PickupSessionStore.markBoarded())
    assertNull(PickupSessionStore.session.value)
  }

  @Test
  fun markBoarded_returnsStateBeforeAndSetsBoarded() {
    PickupSessionStore.start(session)
    val before = PickupSessionStore.markBoarded()
    assertEquals(false, before!!.boarded)
    assertTrue(PickupSessionStore.session.value!!.boarded)
  }

  @Test
  fun update_changesOnlyWhenSessionExists() {
    PickupSessionStore.update { it.copy(remainingSeconds = 60) }
    assertNull(PickupSessionStore.session.value)
    PickupSessionStore.start(session)
    PickupSessionStore.update { it.copy(remainingSeconds = 60) }
    assertEquals(60, PickupSessionStore.session.value!!.remainingSeconds)
  }
}
