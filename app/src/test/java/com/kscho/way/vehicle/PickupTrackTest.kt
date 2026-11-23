package com.kscho.way.vehicle

import com.kscho.way.domain.model.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 픽업 위치까지 — 내비 안내 없이 남은 시간·거리·도착 어림. */
class PickupTrackTest {
  private val pickup = LatLng(37.5, 127.0)
  // 픽업 위치에서 북쪽으로 약 1,113 m · 556 m · 22 m
  private val far = LatLng(37.51, 127.0)
  private val half = LatLng(37.505, 127.0)
  private val near = LatLng(37.5002, 127.0)

  @Test
  fun progress_usesTheNaviTrip_andShrinksItAsTheCarGetsCloser() {
    val trip = PickupTrack.Trip(meters = 1_500, seconds = 300, from = far)

    val atStart = PickupTrack.progress(far, pickup, trip, now = 1_000L)!!
    assertEquals(1_500, atStart.remainingMeters)
    assertEquals(300, atStart.remainingSeconds)
    assertEquals(1_000L + 300_000L, atStart.arrivalAtMillis)
    assertEquals(far, atStart.location)

    // 직선으로 반쯤 왔으면 남은 길·시간도 반쯤
    val halfway = PickupTrack.progress(half, pickup, trip, now = 2_000L)!!
    assertEquals(750, halfway.remainingMeters, 2)
    assertEquals(150, halfway.remainingSeconds, 1)
  }

  @Test
  fun progress_withoutNaviAnswer_estimatesFromStraightDistance() {
    val p = PickupTrack.progress(far, pickup, trip = null, now = 0L)!!
    assertEquals(1_447, p.remainingMeters, 3) // 1,113 m × 1.3
    assertEquals(207, p.remainingSeconds, 1) // ÷ 7 m/s
  }

  @Test
  fun progress_knowingNothing_isNotReported() {
    assertNull(PickupTrack.progress(null, pickup, trip = null, now = 0L))
  }

  @Test
  fun arrived_onlyWithinThirtyFiveMeters() {
    assertTrue(PickupTrack.arrived(near, pickup))
    assertFalse(PickupTrack.arrived(half, pickup))
    assertFalse(PickupTrack.arrived(null, pickup))
  }

  private fun assertEquals(expected: Int, actual: Int, tolerance: Int) = assertTrue("$actual ≈ $expected", kotlin.math.abs(expected - actual) <= tolerance)
}
