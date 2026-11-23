package com.kscho.way.pickup

import android.app.NotificationManager
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.pickup.PickupEffect
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.voice.AskOutcome
import com.kscho.way.voice.AudioPromptPlayer
import com.kscho.way.voice.PleosAnswerListener
import com.kscho.way.voice.SpokenPrompt
import com.kscho.way.voice.VoiceQuestion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class PickupService : Service() {
  @Inject lateinit var coordinator: PickupCoordinator

  private val main = Handler(Looper.getMainLooper())
  private val scope = MainScope()
  private lateinit var audio: AudioManager
  private lateinit var player: AudioPromptPlayer
  private lateinit var listener: PleosAnswerListener
  private lateinit var question: VoiceQuestion
  private var focusRequest: AudioFocusRequest? = null
  private val speech = Channel<SpokenPrompt>(Channel.UNLIMITED)
  private var shownOngoing: Pair<String, String>? = null
  private var stopAfterSpeech = false
  private var speaking: Job? = null
  private val stopAnyway = Runnable { stopSelf() }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    PickupNotifications.createChannels(this)
    val now = coordinator.state.value
    ServiceCompat.startForeground(
      this,
      PickupNotifications.ONGOING_ID,
      PickupNotifications.ongoing(this, now?.plan?.passenger?.callName, now?.plan?.pickupPoint?.description?.title),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
    audio = getSystemService(AudioManager::class.java)
    player = AudioPromptPlayer(this, ASK_AUDIO)
    listener = PleosAnswerListener(this)
    question = VoiceQuestion(player, listener)
    scope.launch { coordinator.state.collect(::onState) }
    scope.launch { coordinator.effects.collect(::onEffect) }
    scope.launch {
      for (prompt in speech) {
        val job = launch { speak(prompt) }
        speaking = job
        job.join()
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

  override fun onDestroy() {
    main.removeCallbacks(stopAnyway)
    question.cancel()
    abandonFocus()
    player.release()
    listener.release()
    speech.close()
    scope.cancel()
    notifications().cancel(PickupNotifications.CALL_OFFER_ID)
    super.onDestroy()
  }

  private fun onState(state: PickupState?) {
    if (state == null) {
      Log.i(TAG, "픽업 끝 — 하던 안내를 마치고 서비스 멈춤")
      question.cancel()
      run(SpeechPlan.onPickupClosed(), prompt = null)
      main.removeCallbacks(stopAnyway)
      main.postDelayed(stopAnyway, 15_000)
      return
    }
    if (state.isBoarded) {
      question.cancel()
      notifications().cancel(PickupNotifications.CALL_OFFER_ID)
      PickupNotifications.TEXT_IDS.forEach(notifications()::cancel)
      main.removeCallbacks(stopAnyway)
      main.postDelayed(stopAnyway, 20_000)
      return
    }
    val ongoing = state.plan.passenger.callName to state.plan.pickupPoint.description.title
    if (ongoing != shownOngoing) {
      shownOngoing = ongoing
      notifications().notify(PickupNotifications.ONGOING_ID, PickupNotifications.ongoing(this, ongoing.first, ongoing.second))
    }
  }

  private fun onEffect(effect: PickupEffect) {
    when (effect) {
      is PickupEffect.Announce -> {
        Log.i(TAG, "음성 안내: ${effect.announcement.text}")
        run(SpeechPlan.onAnnounce(effect.announcement), PickupPrompts.announcement(effect.announcement))
      }
      is PickupEffect.OfferCall -> offerCall(effect.announcement.passenger)
      else -> Unit
    }
  }

  private fun run(steps: List<SpeechPlan.Step>, prompt: SpokenPrompt?) {
    for (step in steps) {
      when (step) {
        SpeechPlan.Step.INTERRUPT -> {
          while (speech.tryReceive().isSuccess) Unit
          if (!question.isAsking) speaking?.cancel()
        }
        SpeechPlan.Step.SAY -> prompt?.let { speech.trySend(it) }
        SpeechPlan.Step.STOP_AFTER -> {
          stopAfterSpeech = true
          speech.trySend(STOP)
        }
      }
    }
  }

  private suspend fun speak(prompt: SpokenPrompt) {
    if (prompt === STOP) {
      if (stopAfterSpeech) stopSelf()
      return
    }
    if (question.isAsking) return
    val request =
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(ASK_AUDIO).build()
    if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      Log.w(TAG, "오디오 포커스를 못 얻어 안내를 건너뜀: ${prompt.text}")
      return
    }
    try {
      withTimeoutOrNull(5_000L + prompt.text.length * 200L) {
        suspendCancellableCoroutine { cont ->
          player.play(prompt) { played ->
            if (!played) Log.w(TAG, "안내 소리를 내지 못함(에뮬 TTS 등): ${prompt.text}")
            if (cont.isActive) cont.resume(Unit)
          }
          cont.invokeOnCancellation { main.post { if (!question.isAsking) player.stop() } }
        }
      }
    } finally {
      audio.abandonAudioFocusRequest(request)
    }
  }

  private fun offerCall(passenger: Contact) {
    if (!WayScreen.isVisible(this)) notifications().notify(PickupNotifications.CALL_OFFER_ID, PickupNotifications.callOffer(this, passenger))
    if (!requestFocus()) {
      Log.w(TAG, "오디오 포커스를 못 얻어(다른 음성 안내·Gleo 대화 중) 화면 알림만 남김")
      return
    }
    question.ask(PickupPrompts.callOffer(passenger), PickupPrompts.callOfferRetry(passenger)) { outcome ->
      abandonFocus()
      onCallOfferAnswered(passenger, outcome)
    }
  }

  private fun onCallOfferAnswered(passenger: Contact, outcome: AskOutcome) {
    Log.i(TAG, "전화 연결 제안 대답: $outcome")
    when (outcome) {
      AskOutcome.YES -> {
        if (coordinator.state.value?.isBoarded != false) return
        if (!WayScreen.isVisible(this)) {
          Log.w(TAG, "Way 가 화면에 없어 전화 화면을 못 띄움 — 알림 버튼으로 안내")
          notifications().notify(PickupNotifications.CALL_OFFER_ID, PickupNotifications.callTapNeeded(this, passenger))
          return
        }
        try {
          startActivity(PassengerCall.dialIntent(passenger.phone).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          notifications().cancel(PickupNotifications.CALL_OFFER_ID)
        } catch (e: ActivityNotFoundException) {
          Log.w(TAG, "전화 화면이 없어 알림 버튼만 남김", e)
        }
      }
      AskOutcome.NO -> notifications().cancel(PickupNotifications.CALL_OFFER_ID)
      AskOutcome.NO_ANSWER,
      AskOutcome.NOT_ASKED -> Unit
    }
  }

  private fun requestFocus(): Boolean {
    val request =
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ASK_AUDIO)
        .setOnAudioFocusChangeListener(
          { change ->
            Log.i(TAG, "오디오 포커스 변화 $change")
            if (change < 0 && question.isAsking) {
              question.cancel()
              abandonFocus()
            }
          },
          main,
        )
        .build()
    focusRequest = request
    return audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  private fun abandonFocus() {
    focusRequest?.let { audio.abandonAudioFocusRequest(it) }
    focusRequest = null
  }

  private fun notifications() = getSystemService(NotificationManager::class.java)

  companion object {
    private const val TAG = "WayPickup"

    private val STOP = SpokenPrompt("", "")

    private val ASK_AUDIO: AudioAttributes =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun start(context: Context): Boolean =
      try {
        ContextCompat.startForegroundService(context, Intent(context, PickupService::class.java))
        true
      } catch (e: IllegalStateException) {
        Log.w(TAG, "픽업 서비스를 시작하지 못함(앱이 뒤에 있음)", e)
        false
      }
  }
}
