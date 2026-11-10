package com.kscho.way.data.sms

import com.kscho.way.domain.messaging.PassengerText
import com.kscho.way.domain.model.MyVehicle
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** 와이어프레임 P-00(Pleos 문자) 문구 그대로인지. */
class PickupTextsTest {
  private val link = "http://localhost:8080/p/abc"

  @Test
  fun departure_followsWireframe() {
    assertEquals(
      "[WAY] 김도윤 님이 데리러 가고 있어요.\n" +
        "차량: 흰색 아반떼 12가 3456\n" +
        "픽업 위치: 신촌 XX 빌라 골목 입구 앞\n" +
        "도착 예정: 09:50\n" +
        "실시간 위치 보기 $link",
      PickupTexts.departure(
        driverName = "김도윤",
        vehicleDescription = "흰색 아반떼",
        plateNumber = "12가 3456",
        pickupTitle = "신촌 XX 빌라 골목 입구 앞",
        arrivalAt = "09:50",
        link = link,
      ),
    )
  }

  @Test
  fun etaChanged_followsWireframe() {
    assertEquals("[WAY] 도착 시간이 09:50 → 09:55로 바뀌었어요.\n$link", PickupTexts.etaChanged("09:50", "09:55", link))
  }

  @Test
  fun arrivingSoon_followsWireframe() {
    assertEquals(
      "[WAY] 곧 도착해요! 약 5분 뒤 픽업 위치에 도착해요. 지금 나와서 기다려 주세요.\n$link",
      PickupTexts.arrivingSoon(link),
    )
  }

  private val seoul = ZoneId.of("Asia/Seoul")

  private fun at(text: String) = LocalDateTime.parse(text).atZone(seoul).toInstant().toEpochMilli()

  @Test
  fun of_turnsCarTextsIntoSameWording() {
    val vehicle = MyVehicle("흰색 아반떼", "12가 3456", "김도윤")
    assertEquals(
      PickupTexts.departure("김도윤", "흰색 아반떼", "12가 3456", "신촌 XX 빌라 골목 입구 앞", "09:50", link),
      PickupTexts.of(PassengerText.Departure(link, "김도윤", vehicle, "신촌 XX 빌라 골목 입구 앞", at("2026-11-09T09:50:00")), seoul),
    )
    assertEquals(
      PickupTexts.etaChanged("09:50", "09:55", link),
      PickupTexts.of(PassengerText.EtaChanged(link, at("2026-11-09T09:50:00"), at("2026-11-09T09:55:00")), seoul),
    )
    assertEquals(PickupTexts.arrivingSoon(link), PickupTexts.of(PassengerText.ArrivingSoon(link), seoul))
  }
}
