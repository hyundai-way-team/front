package com.kscho.way.pickup

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.pickup.Announcement
import com.kscho.way.pickup.SpeechPlan.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechPlanTest {
  private val seoyeon = Contact("c1", "이서연", "010-0000-1234")

  @Test
  fun boardingLine_isSaid_andServiceWaitsForTheFiveSecondOutcome() {
    assertEquals(listOf(Step.SAY), SpeechPlan.onAnnounce(Announcement.Boarded("연남동 00 식당")))
    assertEquals(listOf(Step.SAY), SpeechPlan.onAnnounce(Announcement.Boarded(null)))
  }

  @Test
  fun cancelLine_cutsTheStaleBoardingLine_thenStopsAfterSaying() {
    assertEquals(listOf(Step.INTERRUPT, Step.SAY, Step.STOP_AFTER), SpeechPlan.onAnnounce(Announcement.GuidanceEnded))
  }

  @Test
  fun closingThePickup_finishesWhatIsBeingSaid() {
    assertEquals(listOf(Step.STOP_AFTER), SpeechPlan.onPickupClosed())
  }

  @Test
  fun otherLines_areJustSaid() {
    assertEquals(listOf(Step.SAY), SpeechPlan.onAnnounce(Announcement.LinkChecked(seoyeon)))
  }
}
