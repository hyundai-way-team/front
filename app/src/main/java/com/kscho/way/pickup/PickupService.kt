package com.kscho.way.pickup

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kscho.way.voice.AskOutcome
import com.kscho.way.voice.AudioPromptPlayer
import com.kscho.way.voice.PleosAnswerListener
import com.kscho.way.voice.VoiceQuestion
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 픽업이 진행되는 동안 Way 를 살려 두는 포그라운드 서비스.
 * - 내비가 앞에 나와 Way 가 뒤로 가도 얼지 않게 한다(Android 14 프리저 — 도어·내비 콜백이 멈추던 문제).
 * - MVP 3-9: 정차 후 1분 대기 중 15초 남았는데 미탑승이면 "OO님이 아직 도착하지 않았어요. 전화를 걸까요?"를
 *   앱이 먼저 묻고(Gleo 는 앱이 먼저 말을 걸 수 없음) 대답을 들어 전화 화면을 띄운다.
 *
 * 픽업을 시작하는 곳(출발 버튼)에서 [PickupSessionStore.start] 뒤에 [start]를 부른다.
 * 픽업이 끝나거나(clear) 탑승하면(boarded) 스스로 멈춘다.
 */
class PickupService : Service() {
  private val main = Handler(Looper.getMainLooper())
  private val scope = MainScope()
  private lateinit var audio: AudioManager
  private lateinit var player: AudioPromptPlayer
  private lateinit var listener: PleosAnswerListener
  private lateinit var question: VoiceQuestion
  private var focusRequest: AudioFocusRequest? = null
  private var callOfferAt: Long? = null
  /** '픽업 중' 알림에 지금 보이는 내용 — 바뀔 때만 다시 올린다. */
  private var shownOngoing: Pair<String, String>? = null
  private val callOffer = Runnable { offerCall() }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    PickupNotifications.createChannels(this)
    ServiceCompat.startForeground(
      this,
      PickupNotifications.ONGOING_ID,
      PickupNotifications.ongoing(this, PickupSessionStore.session.value),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
    audio = getSystemService(AudioManager::class.java)
    player = AudioPromptPlayer(this, ASK_AUDIO)
    listener = PleosAnswerListener(this)
    question = VoiceQuestion(player, listener)
    scope.launch { PickupSessionStore.session.collect(::onSession) }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

  override fun onDestroy() {
    main.removeCallbacks(callOffer)
    question.cancel()
    abandonFocus()
    player.release()
    listener.release()
    scope.cancel()
    notifications().cancel(PickupNotifications.CALL_OFFER_ID)
    super.onDestroy()
  }

  private fun onSession(session: PickupSession?) {
    if (session == null || session.boarded) {
      Log.i(TAG, if (session == null) "픽업 끝 — 서비스 멈춤" else "탑승 확인 — 서비스 멈춤")
      question.cancel()
      notifications().cancel(PickupNotifications.CALL_OFFER_ID)
      stopSelf()
      return
    }
    val ongoing = session.passengerName to session.pickupPlaceName
    if (ongoing != shownOngoing) {
      shownOngoing = ongoing
      notifications().notify(PickupNotifications.ONGOING_ID, PickupNotifications.ongoing(this, session))
    }
    scheduleCallOffer(PickupWaitTimer.callOfferAt(session))
  }

  private fun scheduleCallOffer(at: Long?) {
    if (at == callOfferAt) return
    main.removeCallbacks(callOffer)
    callOfferAt = at
    if (at == null) return
    val delay = (at - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    Log.i(TAG, "전화 연결 제안 ${delay}ms 뒤")
    main.postDelayed(callOffer, delay)
  }

  private fun offerCall() {
    callOfferAt = null
    if (!PickupSessionStore.markCallOffered()) return
    val session = PickupSessionStore.session.value ?: return
    // 화면에도 같은 질문 — 음성을 못 알아들어도 버튼으로 걸 수 있다
    notifications().notify(PickupNotifications.CALL_OFFER_ID, PickupNotifications.callOffer(this, session))
    if (!requestFocus()) {
      Log.w(TAG, "오디오 포커스를 못 얻어(다른 음성 안내·Gleo 대화 중) 화면 알림만 남김")
      return
    }
    question.ask(PickupPrompts.callOffer(session.passengerName), PickupPrompts.callOfferRetry(session.passengerName)) {
      outcome ->
      abandonFocus()
      onCallOfferAnswered(outcome)
    }
  }

  private fun onCallOfferAnswered(outcome: AskOutcome) {
    Log.i(TAG, "전화 연결 제안 대답: $outcome")
    when (outcome) {
      AskOutcome.YES -> {
        val session = PickupSessionStore.session.value ?: return
        if (session.boarded) return
        // 안드로이드는 화면에 안 보이는 앱이 다른 화면을 띄우는 걸 말없이 막는다(포그라운드 서비스여도).
        // 내비가 앞에 있으면 전화 화면 대신 헤드업 알림의 '전화 걸기'를 남긴다.
        if (!isOnScreen()) {
          Log.w(TAG, "Way 가 화면에 없어 전화 화면을 못 띄움 — 알림 버튼으로 안내")
          notifications().notify(PickupNotifications.CALL_OFFER_ID, PickupNotifications.callTapNeeded(this, session))
          return
        }
        try {
          startActivity(PassengerCall.dialIntent(session.passengerPhone).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

  /** Way 화면이 지금 보이는지(분할 화면 한쪽 포함). 보여야 전화 화면을 띄울 수 있다. */
  private fun isOnScreen(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true // Pleos 는 API 34 — 옛 OS 는 그냥 시도
    return getSystemService(ActivityManager::class.java).appTasks.any {
      runCatching { it.taskInfo.isVisible }.getOrDefault(false)
    }
  }

  private fun notifications() = getSystemService(NotificationManager::class.java)

  companion object {
    private const val TAG = "WayPickup"

    /** 음성 질문(VOICE_COMMAND 컨텍스트 — 재생 중 음악은 멈추고, 내비 안내와는 겹칠 수 있다). */
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
