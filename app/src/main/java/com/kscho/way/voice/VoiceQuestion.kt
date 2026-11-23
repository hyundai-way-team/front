package com.kscho.way.voice

data class SpokenPrompt(val assetName: String, val text: String)

interface PromptPlayer {
  fun play(prompt: SpokenPrompt, onFinished: (played: Boolean) -> Unit)

  fun stop()
}

interface AnswerListener {
  fun listen(onHeard: (String?) -> Unit)

  fun stop()
}

enum class AskOutcome {
  YES,
  NO,
  NO_ANSWER,
  NOT_ASKED,
}

class VoiceQuestion(
  private val player: PromptPlayer,
  private val listener: AnswerListener,
  private val maxAttempts: Int = 2,
) {
  private var turn = 0
  private var onOutcome: ((AskOutcome) -> Unit)? = null

  val isAsking: Boolean
    get() = onOutcome != null

  fun ask(first: SpokenPrompt, retry: SpokenPrompt, onOutcome: (AskOutcome) -> Unit) {
    if (isAsking) return
    this.onOutcome = onOutcome
    attempt(first, retry, number = 1)
  }

  fun cancel() {
    if (!isAsking) return
    onOutcome = null
    turn++
    player.stop()
    listener.stop()
  }

  private fun attempt(prompt: SpokenPrompt, retry: SpokenPrompt, number: Int) {
    val myTurn = ++turn
    player.play(prompt) { played ->
      if (myTurn != turn) return@play
      if (!played) {
        finish(if (number == 1) AskOutcome.NOT_ASKED else AskOutcome.NO_ANSWER)
        return@play
      }
      listener.listen { heard ->
        if (myTurn != turn) return@listen
        when (heard?.let(YesNoParser::parse) ?: YesNo.UNCLEAR) {
          YesNo.YES -> finish(AskOutcome.YES)
          YesNo.NO -> finish(AskOutcome.NO)
          YesNo.UNCLEAR ->
            if (number < maxAttempts) attempt(retry, retry, number + 1) else finish(AskOutcome.NO_ANSWER)
        }
      }
    }
  }

  private fun finish(outcome: AskOutcome) {
    val callback = onOutcome ?: return
    onOutcome = null
    turn++
    callback(outcome)
  }
}
