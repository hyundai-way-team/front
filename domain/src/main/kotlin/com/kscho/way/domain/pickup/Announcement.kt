package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.text.Josa

sealed interface Announcement {
  val text: String

  data class LinkChecked(val passenger: Contact) : Announcement {
    override val text = "${passenger.callName} 님이 픽업 위치를 확인했어요"
  }

  data class PassengerLate(val passenger: Contact) : Announcement {
    override val text = "${passenger.callName} 님이 조금 늦는대요"
  }

  data class ArrivingSoonSent(val passenger: Contact) : Announcement {
    override val text = "${passenger.callName} 님에게 곧 도착한다고 알렸어요"
  }

  data class CallOffer(val passenger: Contact) : Announcement {
    override val text = "${passenger.callName} 님이 아직 도착하지 않았어요. 전화를 걸까요?"
  }

  data class Boarded(val destinationName: String? = null) : Announcement {
    override val text =
      if (destinationName != null) "탑승을 확인했어요. ${PickupPolicy.CONNECT_AFTER_BOARDING_SECONDS}초 뒤 ${Josa.ro(destinationName)} 안내를 시작할게요."
      else "탑승을 확인했어요. ${PickupPolicy.CONNECT_AFTER_BOARDING_SECONDS}초 뒤 기본 내비로 연결할게요."
  }

  data object GuidanceEnded : Announcement {
    override val text = "탑승을 확인했어요. 안내를 종료할게요."
  }
}

data class SpokenAnnouncement(val announcement: Announcement, val atMillis: Long)
