package com.kscho.way.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Pleos SpeechToText(온디바이스)로 운전자 대답을 한 번 듣는다.
 * 온디바이스 모드는 앱 등록·클라이언트 시크릿이 필요 없고 STT_SERVICE 권한만 있으면 된다(에뮬에서 확인).
 * 말이 없으면 SDK 가 끝 신호를 주지 않으므로 [listenMillis] 뒤 직접 멈추고 null 을 준다.
 */
class PleosAnswerListener(
  context: Context,
  private val listenMillis: Long = 7_000L,
) : AnswerListener {
  private val main = Handler(Looper.getMainLooper())
  private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
  private var state = State.NONE
  private var onHeard: ((String?) -> Unit)? = null
  private var recognizing = false
  private val timeout = Runnable { deliver(null, reason = "${listenMillis}ms 동안 대답 없음") }
  private val startRequest = Runnable { request() }

  override fun listen(onHeard: (String?) -> Unit) {
    stop()
    this.onHeard = onHeard
    when (state) {
      // 질문 끝소리가 마이크에 섞이지 않게 잠깐 쉬고 듣는다
      State.READY -> main.postDelayed(startRequest, GAP_AFTER_PROMPT_MILLIS)
      State.FAILED -> deliver(null, reason = "STT 초기화 실패")
      State.INITIALIZING -> Unit // 초기화가 끝나면 시작
      State.NONE -> init()
    }
  }

  override fun stop() {
    onHeard = null
    main.removeCallbacks(startRequest)
    main.removeCallbacks(timeout)
    if (recognizing) runCatching { stt.stop() }
    recognizing = false
  }

  /** 서비스가 끝날 때. */
  fun release() {
    stop()
    if (state == State.READY) {
      runCatching { stt.removeListener(results) }
      runCatching { stt.release() }
    }
    state = State.NONE
  }

  private fun init() {
    state = State.INITIALIZING
    stt.initialize(
      object : OnInitializeListener {
        override fun onSuccess() {
          main.post {
            stt.addListener(results)
            stt.setLocale(Locale.KOREA)
            state = State.READY
            if (onHeard != null) request()
          }
        }

        override fun onFailure(errorType: ErrorType) {
          main.post {
            Log.w(TAG, "STT 초기화 실패 $errorType")
            state = State.FAILED
            deliver(null, reason = "STT 초기화 실패")
          }
        }
      }
    )
  }

  private fun request() {
    if (onHeard == null) return
    recognizing = true
    main.postDelayed(timeout, listenMillis)
    Log.i(TAG, "듣기 시작")
    stt.request()
  }

  private fun deliver(text: String?, reason: String) {
    val callback = onHeard ?: return
    if (recognizing && text == null) runCatching { stt.stop() }
    onHeard = null
    recognizing = false
    main.removeCallbacks(timeout)
    main.removeCallbacks(startRequest)
    Log.i(TAG, "들은 말 '${text.orEmpty()}' ($reason)")
    callback(text)
  }

  private val results =
    object : ResultListener {
      override fun onReady() = Unit

      override fun onStartedRecognition() = Unit

      override fun onUpdated(stt: String, completed: Boolean) {
        if (completed) main.post { deliver(stt.takeIf { it.isNotBlank() }, reason = "인식 완료") }
      }

      override fun onUpdatedEpdData(on: Long, off: Long) = Unit

      override fun onUpdatedRms(rms: Double) = Unit

      override fun onEndedRecognition() = Unit

      override fun onError(errorType: ErrorType) {
        main.post { deliver(null, reason = "STT 오류 $errorType") }
      }
    }

  private enum class State { NONE, INITIALIZING, READY, FAILED }

  private companion object {
    const val TAG = "WayVoice"
    const val GAP_AFTER_PROMPT_MILLIS = 300L
  }
}
