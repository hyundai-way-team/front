package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.Contact

/** 운전자 음성 안내(기획 3-8). 다이얼로그보다 음성을 우선한다. */
sealed interface Announcement {
  val text: String

  /** 동승자가 링크를 열었다. */
  data class LinkChecked(val passenger: Contact) : Announcement {
    override val text = "${passenger.callName} 님이 픽업 위치를 확인했어요"
  }

  /** 동승자가 웹에서 '늦어요'를 눌렀다. */
  data class PassengerLate(val passenger: Contact) : Announcement {
    override val text = "${passenger.callName} 님이 조금 늦는대요"
  }

  /** 도착 5분 전 문자를 보냈다. */
  data class ArrivingSoonSent(val passenger: Contact) : Announcement {
    override val text = "${passenger.callName} 님에게 곧 도착한다고 알렸어요"
  }

  /** 대기 15초 남았는데 미탑승 — 대답을 듣는 질문. */
  data class CallOffer(val passenger: Contact) : Announcement {
    override val text = "${passenger.callName} 님이 아직 도착하지 않았어요. 전화를 걸까요?"
  }

  data object Boarded : Announcement {
    override val text = "탑승을 확인했어요. 안내를 종료할게요"
  }
}

data class SpokenAnnouncement(val announcement: Announcement, val atMillis: Long)
