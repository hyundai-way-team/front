package com.kscho.way.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceQuestionTest {
  private val first = SpokenPrompt("call_offer", "이서연님이 아직 도착하지 않았어요. 전화를 걸까요?")
  private val retry = SpokenPrompt("call_offer_retry", "잘 못 들었어요. 이서연님에게 전화를 걸까요?")

  /** 시험용 재생기: 부른 질문을 적어 두고, 끝 신호는 테스트가 낸다. */
  private class FakePlayer : PromptPlayer {
    val played = mutableListOf<SpokenPrompt>()
    var stopped = 0
    private var pending: ((Boolean) -> Unit)? = null

    override fun play(prompt: SpokenPrompt, onFinished: (played: Boolean) -> Unit) {
      played += prompt
      pending = onFinished
    }

    override fun stop() {
      stopped++
    }

    fun finish(played: Boolean = true) = pending!!.also { pending = null }.invoke(played)
  }

  /** 시험용 듣기: 들은 말은 테스트가 넣는다. */
  private class FakeListener : AnswerListener {
    var listens = 0
    var stopped = 0
    private var pending: ((String?) -> Unit)? = null

    override fun listen(onHeard: (String?) -> Unit) {
      listens++
      pending = onHeard
    }

    override fun stop() {
      stopped++
    }

    fun hear(text: String?) = pending!!.also { pending = null }.invoke(text)
  }

  private val player = FakePlayer()
  private val listener = FakeListener()
  private val question = VoiceQuestion(player, listener)
  private var outcome: AskOutcome? = null

  private fun ask() = question.ask(first, retry) { outcome = it }

  @Test
  fun listensOnlyAfterPromptFinishes() {
    ask()
    assertEquals(listOf(first), player.played)
    assertEquals(0, listener.listens)
    player.finish()
    assertEquals(1, listener.listens)
  }

  @Test
  fun yesAnswer_givesYes() {
    ask()
    player.finish()
    listener.hear("응 전화해줘")
    assertEquals(AskOutcome.YES, outcome)
    assertFalse(question.isAsking)
  }

  @Test
  fun noAnswer_givesNo() {
    ask()
    player.finish()
    listener.hear("아니 괜찮아")
    assertEquals(AskOutcome.NO, outcome)
  }

  @Test
  fun unclearAnswer_asksOnceMoreWithRetryPrompt() {
    ask()
    player.finish()
    listener.hear("어디야")
    assertNull(outcome)
    assertEquals(listOf(first, retry), player.played)
    player.finish()
    listener.hear("응")
    assertEquals(AskOutcome.YES, outcome)
    assertEquals(2, listener.listens)
  }

  @Test
  fun silenceTwice_givesNoAnswer() {
    ask()
    player.finish()
    listener.hear(null)
    player.finish()
    listener.hear(null)
    assertEquals(AskOutcome.NO_ANSWER, outcome)
    assertEquals(2, player.played.size)
  }

  @Test
  fun firstPromptNotPlayed_givesNotAskedWithoutListening() {
    ask()
    player.finish(played = false)
    assertEquals(AskOutcome.NOT_ASKED, outcome)
    assertEquals(0, listener.listens)
  }

  @Test
  fun retryPromptNotPlayed_givesNoAnswer() {
    ask()
    player.finish()
    listener.hear(null)
    player.finish(played = false)
    assertEquals(AskOutcome.NO_ANSWER, outcome)
    assertEquals(1, listener.listens)
  }

  @Test
  fun cancel_stopsBothAndIgnoresLateCallbacks() {
    ask()
    player.finish()
    question.cancel()
    assertEquals(1, player.stopped)
    assertEquals(1, listener.stopped)
    assertFalse(question.isAsking)
    listener.hear("응") // 취소 뒤 늦게 온 결과
    assertNull(outcome)
  }

  @Test
  fun askWhileAsking_isIgnored() {
    ask()
    var second: AskOutcome? = null
    question.ask(first, retry) { second = it }
    assertEquals(1, player.played.size)
    assertTrue(question.isAsking)
    assertNull(second)
  }
}
