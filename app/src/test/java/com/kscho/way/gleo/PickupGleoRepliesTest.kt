package com.kscho.way.gleo


import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupGleoRepliesTest {
  private val session =
    PickupSummary(
      passengerName = "이서연",
      passengerPhone = "01012345678",
      pickupPlaceName = "역삼역 3번 출구",
      remainingSeconds = 410,
      passengerCheckedLink = true,
      passengerSharingLocation = false,
    )

  // ── 픽업 상황 알려줘

  @Test
  fun status_noSession_isSuccessWithoutData() {
    val reply = PickupGleoReplies.status(null)
    assertEquals(GleoActions.GET_PICKUP_STATUS, reply.functionName)
    assertEquals(GleoStatus.SUCCESS, reply.status)
    assertEquals("There is no pickup in progress.", reply.message)
    assertNull(reply.data)
  }

  @Test
  fun status_inProgress_saysPassengerPlaceMinutesAndLinkState() {
    val reply = PickupGleoReplies.status(session)
    assertEquals(GleoStatus.SUCCESS, reply.status)
    assertEquals(
      "Picking up 이서연 at 역삼역 3번 출구. Arriving in about 7 minutes. " +
        "이서연 has checked the pickup location but is not sharing location.",
      reply.message,
    )
    val row = reply.data!!.single()
    assertEquals("이서연", row["passengerName"])
    assertEquals("7", row["minutesToArrival"])
    assertEquals("true", row["checkedPickupLink"])
    assertEquals("false", row["boarded"])
    assertFalse("전화번호는 Gleo 로 보내지 않는다", row.values.any { it.contains("0101234") })
  }

  @Test
  fun status_unknownEtaAndLinkNotOpened() {
    val reply = PickupGleoReplies.status(session.copy(remainingSeconds = null, passengerCheckedLink = false))
    assertEquals(
      "Picking up 이서연 at 역삼역 3번 출구. Arrival time is not available yet. " +
        "이서연 has not opened the pickup link yet.",
      reply.message,
    )
  }

  @Test
  fun status_arrived_whenRemainingUnderOneMinute() {
    val reply = PickupGleoReplies.status(session.copy(remainingSeconds = 20, passengerSharingLocation = true))
    assertEquals(
      "Picking up 이서연 at 역삼역 3번 출구. Arriving now. " +
        "이서연 has checked the pickup location and is sharing location.",
      reply.message,
    )
  }

  @Test
  fun status_boarded() {
    val reply = PickupGleoReplies.status(session.copy(boarded = true))
    assertEquals("이서연 has already boarded.", reply.message)
  }

  // ── 탑승 완료

  @Test
  fun boarding_noSession_fails() {
    val reply = PickupGleoReplies.boarding(null)
    assertEquals(GleoActions.COMPLETE_BOARDING, reply.functionName)
    assertEquals(GleoStatus.FAIL, reply.status)
    assertEquals("There is no pickup in progress to complete.", reply.message)
  }

  @Test
  fun boarding_inProgress_succeeds() {
    val reply = PickupGleoReplies.boarding(session)
    assertEquals(GleoStatus.SUCCESS, reply.status)
    assertEquals("Confirmed that 이서연 has boarded. Pickup guidance and location sharing have ended.", reply.message)
  }

  @Test
  fun boarding_alreadyBoarded_succeedsWithoutRepeating() {
    val reply = PickupGleoReplies.boarding(session.copy(boarded = true))
    assertEquals(GleoStatus.SUCCESS, reply.status)
    assertEquals("Boarding was already confirmed.", reply.message)
  }

  // ── 동승자에게 전화

  @Test
  fun call_noSession_fails() {
    val reply = PickupGleoReplies.call(null, dialerAvailable = true)
    assertEquals(GleoActions.CALL_PASSENGER, reply.functionName)
    assertEquals(GleoStatus.FAIL, reply.status)
    assertEquals("There is no pickup in progress, so there is no passenger to call.", reply.message)
  }

  @Test
  fun call_noDialer_fails() {
    val reply = PickupGleoReplies.call(session, dialerAvailable = false)
    assertEquals(GleoStatus.FAIL, reply.status)
    assertEquals("Phone calls are not available in this vehicle.", reply.message)
  }

  @Test
  fun call_alreadyBoarded_fails() {
    val reply = PickupGleoReplies.call(session.copy(boarded = true), dialerAvailable = true)
    assertEquals(GleoStatus.FAIL, reply.status)
    assertEquals("이서연 has already boarded.", reply.message)
  }

  @Test
  fun call_ok_succeeds() {
    val reply = PickupGleoReplies.call(session, dialerAvailable = true)
    assertEquals(GleoStatus.SUCCESS, reply.status)
    assertEquals("Opening the dialer to call 이서연.", reply.message)
  }

  // ── Gleo 로 보내는 JSON 모양 (data 는 'JSON 객체 배열'을 담은 문자열)

  @Test
  fun toJson_matchesCallbackSchema() {
    val json = Json.parseToJsonElement(PickupGleoReplies.status(session).toJson()).jsonObject
    assertEquals("GET_PICKUP_STATUS", json["functionName"]!!.jsonPrimitive.content)
    assertEquals("success", json["status"]!!.jsonPrimitive.content)
    assertEquals("voice", json["mode"]!!.jsonPrimitive.content)
    assertTrue(json["message"]!!.jsonPrimitive.content.startsWith("Picking up"))
    val data = Json.parseToJsonElement(json["data"]!!.jsonPrimitive.content).jsonArray
    assertEquals("역삼역 3번 출구", data.single().jsonObject["pickupPlace"]!!.jsonPrimitive.content)
  }

  @Test
  fun toJson_omitsDataWhenNull() {
    val json = Json.parseToJsonElement(PickupGleoReplies.status(null).toJson()).jsonObject
    assertFalse(json.containsKey("data"))
  }
}
