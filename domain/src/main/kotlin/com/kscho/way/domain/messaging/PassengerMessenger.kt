package com.kscho.way.domain.messaging

import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.MyVehicle

/** 동승자에게 보낼 문자 — 무엇을 알릴지만 담는다(문구는 구현이 와이어프레임 P-00 대로 만든다). 세 문자 모두 같은 참여 링크. */
sealed interface PassengerText {
  val link: String

  /** ① 출발할 때 — 차량 번호 · 픽업 위치 · 도착 예정 · 참여 링크(기획 3-4) */
  data class Departure(
    override val link: String,
    val driverName: String,
    val vehicle: MyVehicle,
    val pickupTitle: String,
    val arrivalAtMillis: Long,
  ) : PassengerText

  /** ② 도착 시간이 바뀌었을 때(기획 3-5) */
  data class EtaChanged(override val link: String, val beforeMillis: Long, val afterMillis: Long) : PassengerText

  /** ③ 도착 5분 전, 픽업 1건당 1번(기획 3-6) */
  data class ArrivingSoon(override val link: String) : PassengerText
}

/**
 * 동승자에게 문자를 보낸다 — **운전자 번호로, 차에서**(Pleos 문자 · 차에 연결된 휴대폰).
 * 서버는 참여 링크를 만들고 차와 동승자 웹을 실시간으로 잇기만 하고 문자는 보내지 않는다.
 */
interface PassengerMessenger {
  suspend fun send(to: Contact, text: PassengerText)
}
