package com.kscho.way.pickup

import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.pickup.Announcement
import com.kscho.way.pickup.SpeechPlan.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechPlanTest {
  private val seoyeon = Contact("c1", "이서연", "010-0000-1234")

  @Test
  fun boardingLine_isSaid_andServiceWaitsForTheFiveSecondOutcome() {
    // 예전엔 탑승 안내 뒤 바로 멈춰서, D-11 '취소'의 "안내를 종료할게요"를 말할 서비스가 없었다
    assertEquals(listOf(Step.SAY), SpeechPlan.onAnnounce(Announcement.Boarded("연남동 00 식당")))
    assertEquals(listOf(Step.SAY), SpeechPlan.onAnnounce(Announcement.Boarded(null)))
  }

  @Test
  fun cancelLine_cutsTheStaleBoardingLine_thenStopsAfterSaying() {
    // '취소' 뒤에 "5초 뒤 … 안내를 시작할게요"를 끝까지 말하면 안 된다
    assertEquals(listOf(Step.INTERRUPT, Step.SAY, Step.STOP_AFTER), SpeechPlan.onAnnounce(Announcement.GuidanceEnded))
  }

  @Test
  fun closingThePickup_finishesWhatIsBeingSaid() {
    // 5초 뒤 넘기며 픽업이 닫혀도 탑승 안내를 끊지 않는다
    assertEquals(listOf(Step.STOP_AFTER), SpeechPlan.onPickupClosed())
  }

  @Test
  fun otherLines_areJustSaid() {
    assertEquals(listOf(Step.SAY), SpeechPlan.onAnnounce(Announcement.LinkChecked(seoyeon)))
  }
}
