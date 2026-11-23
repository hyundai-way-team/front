package com.kscho.way.data.sms

import com.kscho.way.domain.model.message.PassengerText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 동승자에게 보내는 Pleos 문자 문구 — 와이어프레임 P-00 그대로(기획서상 문구·형식은 '미정').
 * 세 문자 모두 같은 참여 링크로 열린다. 문자는 차가 운전자 번호로 보내고([of]),
 * 로컬 서버·mock 은 같은 문구를 시연용 사본으로만 남긴다.
 */
object PickupTexts {
  val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

  /** 차가 보낼 문자 → 문구. 시각은 [zone](한국) 기준 HH:mm. */
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

  /** ① 출발할 때 */
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

  /** ② ETA 가 바뀌었을 때 */
  fun etaChanged(before: String, after: String, link: String): String = "[WAY] 도착 시간이 $before → ${after}로 바뀌었어요.\n$link"

  /** ③ 도착 5분 전 — 픽업 1건당 1번 */
  fun arrivingSoon(link: String): String = "[WAY] 곧 도착해요! 약 5분 뒤 픽업 위치에 도착해요. 지금 나와서 기다려 주세요.\n$link"
}
