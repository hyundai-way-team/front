package com.kscho.way.pickup

import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.pickup.Announcement
import org.junit.Assert.assertEquals
import org.junit.Test

class PickupPromptsTest {
  private val seoyeon = Contact("c1", "이서연", "01000001234")

  @Test
  fun callOffer_usesWireframeWordingAndRecordedFileName() {
    // 기획 3-8 · 와이어프레임 D-09: "서연 님이 아직 도착하지 않았어요. 전화를 걸까요?"
    val prompt = PickupPrompts.callOffer(seoyeon)
    assertEquals("서연 님이 아직 도착하지 않았어요. 전화를 걸까요?", prompt.text)
    assertEquals("call_offer", prompt.assetName)
  }

  @Test
  fun callOfferRetry_tellsHowToAnswer() {
    val prompt = PickupPrompts.callOfferRetry(seoyeon)
    assertEquals("잘 못 들었어요. 서연 님에게 전화를 걸까요? 응 또는 아니로 답해 주세요.", prompt.text)
    assertEquals("call_offer_retry", prompt.assetName)
  }

  @Test
  fun announcements_readTheSameTextAsTheScreen() {
    assertEquals("서연 님이 픽업 위치를 확인했어요", PickupPrompts.announcement(Announcement.LinkChecked(seoyeon)).text)
    assertEquals("arriving_soon_sent", PickupPrompts.announcement(Announcement.ArrivingSoonSent(seoyeon)).assetName)
    assertEquals("탑승을 확인했어요. 안내를 종료할게요", PickupPrompts.announcement(Announcement.Boarded()).text)
  }
}
