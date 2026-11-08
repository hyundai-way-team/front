package com.kscho.way.voice

/** 들려줄 질문 한 마디. 녹음 파일(assets/voice/<assetName>.*)이 있으면 그걸, 없으면 [text]를 TTS 로. */
data class SpokenPrompt(val assetName: String, val text: String)

interface PromptPlayer {
  /** 질문을 들려준다. 다 들려줬으면 onFinished(true), 들려주지 못했으면 false. */
  fun play(prompt: SpokenPrompt, onFinished: (played: Boolean) -> Unit)

  fun stop()
}

interface AnswerListener {
  /** 운전자 말을 한 번 듣는다. 알아들은 문장, 말이 없거나 실패하면 null. */
  fun listen(onHeard: (String?) -> Unit)

  fun stop()
}

enum class AskOutcome {
  YES,
  NO,
  /** 물었지만 알아들을 대답이 없었다. */
  NO_ANSWER,
  /** 질문을 들려주지 못해 듣지도 않았다. */
  NOT_ASKED,
}

/**
 * 앱이 먼저 묻고 듣는 흐름: 질문 재생 → 끝나면 듣기 → 예/아니오.
 * 못 알아들으면 [maxAttempts]번까지 다시 묻는다. Gleo 를 거치지 않는다(Gleo 는 앱이 먼저 말을 걸 수 없음).
 * 콜백은 한 스레드(메인)에서 온다고 본다.
 */
class VoiceQuestion(
  private val player: PromptPlayer,
  private val listener: AnswerListener,
  private val maxAttempts: Int = 2,
) {
  /** 지금 기다리는 재생·듣기의 번호. 취소·종료 뒤 늦게 온 콜백을 버리는 데 쓴다. */
  private var turn = 0
  private var onOutcome: ((AskOutcome) -> Unit)? = null

  val isAsking: Boolean
    get() = onOutcome != null

  /** 이미 묻는 중이면 무시한다. 취소하면 [onOutcome]은 불리지 않는다. */
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
