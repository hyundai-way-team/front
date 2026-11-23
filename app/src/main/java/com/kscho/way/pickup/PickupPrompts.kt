package com.kscho.way.pickup

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.pickup.Announcement
import com.kscho.way.voice.SpokenPrompt

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
        is Announcement.Boarded -> if (announcement.destinationName != null) "boarded_to_destination" else "boarded_to_navi"
        Announcement.GuidanceEnded -> "guidance_ended"
      },
      announcement.text,
    )
}
