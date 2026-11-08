package com.kscho.way.pickup

import com.kscho.way.voice.SpokenPrompt

/**
 * 픽업 중 운전자에게 하는 음성 질문(MVP 3-8 문구).
 * assetName 은 팀이 녹음해 넣을 파일 이름(assets/voice/call_offer.ogg 등).
 * 녹음 파일엔 동승자 이름을 넣을 수 없으니, 시연 페르소나(이서연) 기준으로 녹음하거나 '동승자'로 녹음한다.
 */
object PickupPrompts {
  fun callOffer(passengerName: String) =
    SpokenPrompt("call_offer", "${passengerName}님이 아직 도착하지 않았어요. 전화를 걸까요?")

  fun callOfferRetry(passengerName: String) =
    SpokenPrompt("call_offer_retry", "잘 못 들었어요. ${passengerName}님에게 전화를 걸까요? 응 또는 아니로 답해 주세요.")
}
