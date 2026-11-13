package com.kscho.way.pickup

import com.kscho.way.domain.pickup.Announcement

/**
 * 음성 안내 줄을 어떻게 이을지(PickupService) — 탑승 뒤엔 하던 말을 마치고 서비스를 멈춘다.
 *
 * 탑승 안내("5초 뒤 … 안내를 시작할게요")를 말한 뒤 바로 멈추면 D-11 '취소'의 "안내를 종료할게요"를 말할 곳이 없다.
 * 그래서 탑승 안내 뒤엔 5초의 결과를 기다린다: 픽업이 닫히면(자동 연결·'안내 시작') 하던 말을 마치고 멈추고,
 * '취소'면 낡은 탑승 안내를 끊고 종료 안내를 말한 뒤 멈춘다.
 */
internal object SpeechPlan {
  enum class Step {
    /** 지금 말하던 것을 끊는다. */
    INTERRUPT,
    /** 이 안내를 줄에 넣는다. */
    SAY,
    /** 줄에 든 말을 다 하면 서비스를 멈춘다. */
    STOP_AFTER,
  }

  fun onAnnounce(announcement: Announcement): List<Step> =
    when (announcement) {
      Announcement.GuidanceEnded -> listOf(Step.INTERRUPT, Step.SAY, Step.STOP_AFTER)
      else -> listOf(Step.SAY)
    }

  /** 픽업이 닫혔다(상태 null). */
  fun onPickupClosed(): List<Step> = listOf(Step.STOP_AFTER)
}
