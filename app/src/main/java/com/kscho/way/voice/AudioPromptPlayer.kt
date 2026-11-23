package com.kscho.way.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.util.Locale

class AudioPromptPlayer(
  context: Context,
  private val attributes: AudioAttributes,
) : PromptPlayer {
  private val app = context.applicationContext
  private val main = Handler(Looper.getMainLooper())
  private var media: MediaPlayer? = null
  private var onDone: ((Boolean) -> Unit)? = null

  private var tts: TextToSpeech? = null
  private var ttsState = TtsState.NONE
  private var ttsStarted = false
  private var pendingText: String? = null
  private val ttsTimeout = Runnable { finish(played = ttsStarted, reason = "TTS 끝 신호 없음") }

  override fun play(prompt: SpokenPrompt, onFinished: (played: Boolean) -> Unit) {
    stop()
    onDone = onFinished
    val asset = findAsset(prompt.assetName)
    if (asset != null && playAsset(asset)) return
    speak(prompt.text)
  }

  override fun stop() {
    onDone = null
    stopOutputs()
  }

  fun release() {
    stop()
    tts?.let { runCatching { it.removeEventListener(ttsEvents) }; runCatching { it.release() } }
    tts = null
    ttsState = TtsState.NONE
  }

  private fun finish(played: Boolean, reason: String) {
    val callback = onDone ?: return
    onDone = null
    Log.i(TAG, "질문 재생 끝 played=$played ($reason)")
    stopOutputs()
    callback(played)
  }

  private fun stopOutputs() {
    main.removeCallbacks(ttsTimeout)
    pendingText = null
    media?.let { runCatching { it.stop() }; it.release() }
    media = null
    if (ttsStarted) runCatching { tts?.stop() }
    ttsStarted = false
  }

  private fun findAsset(name: String): String? {
    val files = runCatching { app.assets.list(ASSET_DIR) }.getOrNull().orEmpty().toSet()
    return AUDIO_EXTENSIONS.map { "$name.$it" }.firstOrNull { it in files }?.let { "$ASSET_DIR/$it" }
  }

  private fun playAsset(path: String): Boolean {
    val player = MediaPlayer()
    return try {
      app.assets.openFd(path).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
      player.setAudioAttributes(attributes)
      player.setOnPreparedListener { it.start() }
      player.setOnCompletionListener { finish(played = true, reason = path) }
      player.setOnErrorListener { _, what, extra ->
        finish(played = false, reason = "$path 재생 오류 $what/$extra")
        true
      }
      player.prepareAsync()
      media = player
      true
    } catch (e: IOException) {
      Log.w(TAG, "$path 를 열 수 없어 TTS 로 읽음", e)
      player.release()
      false
    }
  }

  private fun speak(text: String) {
    when (ttsState) {
      TtsState.READY -> startSpeaking(text)
      TtsState.FAILED -> finish(played = false, reason = "TTS 초기화 실패")
      TtsState.INITIALIZING -> pendingText = text
      TtsState.NONE -> {
        pendingText = text
        initTts()
      }
    }
  }

  private fun initTts() {
    ttsState = TtsState.INITIALIZING
    val engine = TextToSpeech(app, Mode.ON_DEVICE)
    tts = engine
    engine.initialize(
      object : OnInitializeListener {
        override fun onSuccess() {
          main.post {
            engine.addEventListener(ttsEvents)
            engine.setLocale(Locale.KOREA)
            engine.setAudioAttributes(attributes)
            ttsState = TtsState.READY
            pendingText?.let(::startSpeaking)
          }
        }

        override fun onFailure(errorType: ErrorType) {
          main.post {
            Log.w(TAG, "TTS 초기화 실패 $errorType")
            ttsState = TtsState.FAILED
            if (pendingText != null) finish(played = false, reason = "TTS 초기화 실패")
          }
        }
      }
    )
  }

  private fun startSpeaking(text: String) {
    pendingText = null
    ttsStarted = false
    main.postDelayed(ttsTimeout, TTS_BASE_MILLIS + text.length * TTS_MILLIS_PER_CHAR)
    tts?.speak(text, false)
  }

  private val ttsEvents =
    object : EventListener {
      override fun onReady() = Unit

      override fun onStart() {
        main.post { ttsStarted = true }
      }

      override fun onDone() {
        main.post { finish(played = true, reason = "TTS") }
      }

      override fun onStop() = Unit

      override fun onError(errorType: ErrorType) {
        main.post { finish(played = false, reason = "TTS 오류 $errorType") }
      }

      override fun onUpdatedRms(rms: Double) = Unit
    }

  private enum class TtsState { NONE, INITIALIZING, READY, FAILED }

  private companion object {
    const val TAG = "WayVoice"
    const val ASSET_DIR = "voice"
    val AUDIO_EXTENSIONS = listOf("ogg", "m4a", "mp3", "wav")
    const val TTS_BASE_MILLIS = 3_000L
    const val TTS_MILLIS_PER_CHAR = 150L
  }
}
