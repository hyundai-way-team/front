package com.kscho.way.pickup

import org.junit.Assert.assertEquals
import org.junit.Test

class PickupPromptsTest {
  @Test
  fun callOffer_usesMvpWordingAndRecordedFileName() {
    // MVP 3-8 안내 문구: "OO님이 아직 도착하지 않았어요. 전화를 걸까요?"
    val prompt = PickupPrompts.callOffer("이서연")
    assertEquals("이서연님이 아직 도착하지 않았어요. 전화를 걸까요?", prompt.text)
    assertEquals("call_offer", prompt.assetName)
  }

  @Test
  fun callOfferRetry_tellsHowToAnswer() {
    val prompt = PickupPrompts.callOfferRetry("이서연")
    assertEquals("잘 못 들었어요. 이서연님에게 전화를 걸까요? 응 또는 아니로 답해 주세요.", prompt.text)
    assertEquals("call_offer_retry", prompt.assetName)
  }
}
