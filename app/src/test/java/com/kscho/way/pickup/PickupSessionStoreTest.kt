package com.kscho.way.pickup

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun markArrived_keepsFirstArrivalTime() {
    PickupSessionStore.start(session)
    PickupSessionStore.markArrived(1_000L)
    PickupSessionStore.markArrived(9_000L) // 내비 도착 신호가 또 와도 타이머는 처음 정차 기준
    assertEquals(1_000L, PickupSessionStore.session.value!!.arrivedAtMillis)
  }

  @Test
  fun markCallOffered_succeedsOnlyOnce() {
    PickupSessionStore.start(session)
    assertTrue(PickupSessionStore.markCallOffered())
    assertFalse(PickupSessionStore.markCallOffered())
    assertTrue(PickupSessionStore.session.value!!.callOffered)
  }

  @Test
  fun markCallOffered_failsWithoutSessionOrAfterBoarding() {
    assertFalse(PickupSessionStore.markCallOffered())
    PickupSessionStore.start(session.copy(boarded = true))
    assertFalse(PickupSessionStore.markCallOffered())
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
