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
import com.kscho.way.WayApp
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.pickup.PickupEffect
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.voice.AskOutcome
import com.kscho.way.voice.AudioPromptPlayer
import com.kscho.way.voice.PleosAnswerListener
import com.kscho.way.voice.SpokenPrompt
import com.kscho.way.voice.VoiceQuestion
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 픽업이 진행되는 동안 Way 를 살려 두고, 운전자에게 소리로 알린다.
 * - 내비가 앞에 나와 Way 가 뒤로 가도 얼지 않게 한다(Android 14 프리저 — 도어·내비 콜백이 멈추던 문제).
 * - 음성 안내(기획 3-8): 링크 확인 · 곧 도착 문자 보냄 · 탑승 확인 — [PickupEffect.Announce]
 * - 전화 연결 제안(기획 3-9): 15초 남았는데 미탑승이면 묻고 대답을 들어 전화 화면을 띄운다 — [PickupEffect.OfferCall]
 *   (Gleo 는 앱이 먼저 말을 걸 수 없어 앱이 직접 묻는다.)
 *
 * 출발(D-05) 직후 [start] 로 띄운다. 픽업이 끝나면(탑승 안내 뒤·닫기) 스스로 멈춘다.
 */
class PickupService : Service() {
  private val main = Handler(Looper.getMainLooper())
  private val scope = MainScope()
  private val coordinator by lazy { (application as WayApp).container.coordinator }
  private lateinit var audio: AudioManager
  private lateinit var player: AudioPromptPlayer
  private lateinit var listener: PleosAnswerListener
  private lateinit var question: VoiceQuestion
  private var focusRequest: AudioFocusRequest? = null
  private val speech = Channel<SpokenPrompt>(Channel.UNLIMITED)
  /** '픽업 중' 알림에 지금 보이는 내용 — 바뀔 때만 다시 올린다. */
  private var shownOngoing: Pair<String, String>? = null
  private var stopAfterSpeech = false
  /** 지금 말하고 있는 안내. */
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
        // 따로 띄워 두면 '취소' 때 하던 말만 끊을 수 있다(SpeechPlan.INTERRUPT)
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
      // 5초 뒤 넘기며 닫혀도 하던 탑승 안내는 마저 말하고 멈춘다
      Log.i(TAG, "픽업 끝 — 하던 안내를 마치고 서비스 멈춤")
      question.cancel()
      run(SpeechPlan.onPickupClosed(), prompt = null)
      main.removeCallbacks(stopAnyway)
      main.postDelayed(stopAnyway, 15_000) // 안내가 끝 신호를 못 받아도
      return
    }
    if (state.isBoarded) {
      question.cancel()
      notifications().cancel(PickupNotifications.CALL_OFFER_ID)
      PickupNotifications.TEXT_IDS.forEach(notifications()::cancel)
      // D-11 5초의 결과(닫힘 · '취소')를 기다렸다 멈춘다 — SpeechPlan
      main.removeCallbacks(stopAnyway)
      main.postDelayed(stopAnyway, 20_000) // 끝 신호를 못 받아도
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
          // 아직 말하지 않은 줄(낡은 탑승 안내 등)도 버린다
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

  /** 안내 한 줄을 말한다. 음악은 줄여 두고(덕킹), 전화 연결 질문 중이면 건너뛴다(화면 자막은 보인다). */
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
      // 전화 연결 질문이 끼어들면 플레이어가 이 안내를 끊어 끝 신호가 안 오므로 시간 제한을 둔다
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
    // Way 화면이 앞이면 D-09 의 빨간 '전화하기' 버튼이 같은 일을 한다(디자인에 알림 창은 없다).
    // Way 가 가려져 있을 때만(운전자가 다른 앱을 연 경우) 알림으로 같은 질문을 남겨 버튼으로 걸 수 있게
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
        // 안드로이드는 화면에 안 보이는 앱이 다른 화면을 띄우는 걸 말없이 막는다(포그라운드 서비스여도).
        // 내비가 앞에 있으면 전화 화면 대신 헤드업 알림의 '전화 걸기'를 남긴다.
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
      // 대답이 없으면 알림을 남겨 손으로 고를 수 있게
      AskOutcome.NO_ANSWER,
      AskOutcome.NOT_ASKED -> Unit
    }
  }

  /** 질문~대답 동안 음악을 잠시 멈춘다. Gleo 가 불리는 등 포커스를 잃으면 바로 그만 묻는다(Pleos 오디오 포커스 가이드). */
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

    /** 탑승 안내 뒤 서비스를 멈추라는 표시(말하지 않는다). */
    private val STOP = SpokenPrompt("", "")

    /** 음성 안내·질문(VOICE_COMMAND 컨텍스트 — 재생 중 음악은 멈추거나 줄이고, 내비 안내와는 겹칠 수 있다). */
    private val ASK_AUDIO: AudioAttributes =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * 픽업 서비스를 띄운다. 앱 화면이 앞에 있을 때(출발 버튼) 불러야 한다 —
     * 뒤에서 부르면 안드로이드가 포그라운드 서비스 시작을 막는다. 막히면 false.
     */
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
