package com.kscho.way.data.sms

import com.kscho.way.domain.model.message.PassengerText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PickupTexts {
  val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

  fun of(text: PassengerText, zone: ZoneId = SEOUL): String {
    val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    val time = { millis: Long -> hhmm.format(Instant.ofEpochMilli(millis)) }
    return when (text) {
      is PassengerText.Departure ->
        departure(text.driverName, text.vehicle.description, text.vehicle.plateNumber, text.pickupTitle, time(text.arrivalAtMillis), text.link)
      is PassengerText.EtaChanged -> etaChanged(time(text.beforeMillis), time(text.afterMillis), text.link)
      is PassengerText.ArrivingSoon -> arrivingSoon(text.link)
    }
  }

  fun departure(
    driverName: String,
    vehicleDescription: String,
    plateNumber: String,
    pickupTitle: String,
    arrivalAt: String,
    link: String,
  ): String =
    "[WAY] $driverName 님이 데리러 가고 있어요.\n" +
      "차량: $vehicleDescription $plateNumber\n" +
      "픽업 위치: $pickupTitle\n" +
      "도착 예정: $arrivalAt\n" +
      "실시간 위치 보기 $link"

  fun etaChanged(before: String, after: String, link: String): String = "[WAY] 도착 시간이 $before → ${after}로 바뀌었어요.\n$link"

  fun arrivingSoon(link: String): String = "[WAY] 곧 도착해요! 약 5분 뒤 픽업 위치에 도착해요. 지금 나와서 기다려 주세요.\n$link"
}
