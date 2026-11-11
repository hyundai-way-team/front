package com.kscho.way.pickup

import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.pickup.Announcement
import com.kscho.way.voice.SpokenPrompt

/**
 * 운전자 음성 안내(기획 3-8 문구, 와이어프레임처럼 "서연 님").
 * assetName 은 녹음 파일을 넣게 되면 쓸 이름(assets/voice/<이름>.ogg 등). 지금은 파일이 없어 Pleos TTS 로 읽는다.
 */
object PickupPrompts {
  fun callOffer(passenger: Contact) = SpokenPrompt("call_offer", Announcement.CallOffer(passenger).text)

  fun callOfferRetry(passenger: Contact) =
    SpokenPrompt("call_offer_retry", "잘 못 들었어요. ${passenger.callName} 님에게 전화를 걸까요? 응 또는 아니로 답해 주세요.")

  fun announcement(announcement: Announcement) =
    SpokenPrompt(
      when (announcement) {
        is Announcement.LinkChecked -> "link_checked"
        is Announcement.ArrivingSoonSent -> "arriving_soon_sent"
        is Announcement.PassengerLate -> "passenger_late"
        is Announcement.CallOffer -> "call_offer"
        Announcement.Boarded -> "boarded"
      },
      announcement.text,
    )
}
