package com.kscho.way.pickup

import com.kscho.way.domain.pickup.Announcement

internal object SpeechPlan {
  enum class Step {
    INTERRUPT,
    SAY,
    STOP_AFTER,
  }

  fun onAnnounce(announcement: Announcement): List<Step> =
    when (announcement) {
      Announcement.GuidanceEnded -> listOf(Step.INTERRUPT, Step.SAY, Step.STOP_AFTER)
      else -> listOf(Step.SAY)
    }

  fun onPickupClosed(): List<Step> = listOf(Step.STOP_AFTER)
}
