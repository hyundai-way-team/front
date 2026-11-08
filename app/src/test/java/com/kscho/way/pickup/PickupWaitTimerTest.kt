package com.kscho.way.pickup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickupWaitTimerTest {
  private val waiting =
    PickupSession(
      passengerName = "이서연",
      passengerPhone = "01012345678",
      pickupPlaceName = "역삼역 3번 출구",
      remainingSeconds = 0,
      passengerCheckedLink = true,
      passengerSharingLocation = true,
      arrivedAtMillis = 100_000L,
    )

  @Test
  fun callOffer_comesWhen15SecondsOfOneMinuteRemain() {
    // MVP 3-9: 1분 대기 타이머, 15초 남았는데 미탑승이면 전화 연결 제안
    assertEquals(100_000L + 45_000L, PickupWaitTimer.callOfferAt(waiting))
  }

  @Test
  fun noCallOffer_beforeArrival() {
    assertNull(PickupWaitTimer.callOfferAt(waiting.copy(arrivedAtMillis = null)))
  }

  @Test
  fun noCallOffer_afterBoarding() {
    assertNull(PickupWaitTimer.callOfferAt(waiting.copy(boarded = true)))
  }

  @Test
  fun noCallOffer_twice() {
    assertNull(PickupWaitTimer.callOfferAt(waiting.copy(callOffered = true)))
  }
}
