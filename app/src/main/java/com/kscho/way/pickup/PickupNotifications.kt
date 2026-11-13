package com.kscho.way.pickup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.kscho.way.MainActivity
import com.kscho.way.domain.messaging.PassengerText
import com.kscho.way.domain.model.Contact

/**
 * 픽업 중 알림. 운전 관련 알림만 쓴다(Pleos 심사: 운전과 관련 없는 알림 금지).
 *
 * Pleos 헤드업 규칙(에뮬 CarNotificationInjector 에서 확인, 문서엔 없음): 일반 앱 알림은 중요도와 상관없이
 * category 가 navigation·call·msg·connect.system 일 때만 헤드업으로 뜨고,
 * extras "exclude_from_heads_up_notification"=true 면 뜨지 않는다.
 */
object PickupNotifications {
  const val ONGOING_ID = 1001
  const val CALL_OFFER_ID = 1002
  const val NAVI_ID = 1003
  private const val TEXT_ID_BASE = 1010

  /** 문자마다 다른 알림(출발·도착 시간 변경·곧 도착이 서로 덮지 않게). */
  fun textId(text: PassengerText): Int =
    TEXT_ID_BASE +
      when (text) {
        is PassengerText.Departure -> 0
        is PassengerText.EtaChanged -> 1
        is PassengerText.ArrivingSoon -> 2
      }

  /** '문자 보내기' 알림 전부 — 탑승하면 아직 안 누른 것은 지운다(지난 픽업 문자를 나중에 보내지 않게). */
  val TEXT_IDS = TEXT_ID_BASE..TEXT_ID_BASE + 2

  private const val EXTRA_NO_HEADS_UP = "exclude_from_heads_up_notification"
  private const val CHANNEL_ONGOING = "pickup_ongoing"
  private const val CHANNEL_ASK = "pickup_ask"

  fun createChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ONGOING, "픽업 진행", NotificationManager.IMPORTANCE_LOW)
    )
    // 헤드업으로 뜨되, 알림음은 끈다 — 같은 순간 음성 질문이 나간다
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ASK, "픽업 음성 질문", NotificationManager.IMPORTANCE_HIGH).apply {
        setSound(null, null)
        enableVibration(false)
      }
    )
  }

  /** 포그라운드 서비스가 붙들고 있는 '픽업 중' 알림. 헤드업으로 띄우지 않는다(알림 센터에만). */
  fun ongoing(context: Context, passengerName: String?, pickupTitle: String?): Notification =
    NotificationCompat.Builder(context, CHANNEL_ONGOING)
      .setSmallIcon(android.R.drawable.ic_menu_mylocation)
      .setContentTitle(passengerName?.let { "$it 님 픽업 중" } ?: "픽업 중")
      .setContentText(pickupTitle)
      .setContentIntent(openApp(context))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .addExtras(Bundle().apply { putBoolean(EXTRA_NO_HEADS_UP, true) })
      .build()

  /**
   * 전화 연결 제안. 음성 질문과 같은 내용을 헤드업으로 띄우고, 음성 대답이 안 될 때 누를 버튼을 둔다.
   * 헤드업으로 뜨는 category 중 픽업 안내에 가장 가까운 navigation 을 쓴다.
   */
  fun callOffer(context: Context, passenger: Contact): Notification =
    callNotification(context, passenger, "${passenger.callName} 님이 아직 도착하지 않았어요", "전화를 걸까요? '응' 또는 '아니'로 답해 주세요")
      .setOnlyAlertOnce(true)
      .build()

  /** '응'이라고 했지만 Way 가 화면 뒤에 있어 전화 화면을 띄울 수 없을 때 — 다시 헤드업으로 띄워 누르게 한다. */
  fun callTapNeeded(context: Context, passenger: Contact): Notification =
    callNotification(context, passenger, "${passenger.callName} 님에게 전화", "'전화 걸기'를 누르면 전화 화면이 열려요").build()

  private fun callNotification(context: Context, passenger: Contact, title: String, text: String) =
    NotificationCompat.Builder(context, CHANNEL_ASK)
      .setSmallIcon(android.R.drawable.sym_action_call)
      .setContentTitle(title)
      .setContentText(text)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
      .setContentIntent(dial(context, passenger))
      .addAction(0, "전화 걸기", dial(context, passenger))
      .setAutoCancel(true)

  /**
   * Way 가 화면 뒤에 있어(내비가 앞) 문자 보내기 화면을 직접 못 띄울 때 — 누르면 Pleos 문자 화면이 열린다.
   * 헤드업으로 뜨는 category 중 msg 를 쓴다.
   */
  fun textNeeded(context: Context, passenger: Contact, text: PassengerText, send: Intent): Notification {
    val what =
      when (text) {
        is PassengerText.Departure -> "출발 문자"
        is PassengerText.EtaChanged -> "도착 시간 변경 문자"
        is PassengerText.ArrivingSoon -> "'곧 도착해요' 문자"
      }
    val open = PendingIntent.getActivity(context, textId(text), send, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return NotificationCompat.Builder(context, CHANNEL_ASK)
      .setSmallIcon(android.R.drawable.sym_action_email)
      .setContentTitle("${passenger.callName} 님에게 $what 보내기")
      .setContentText("누르면 문구가 채워진 Pleos 문자 화면이 열려요")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setContentIntent(open)
      .addAction(0, "문자 보내기", open)
      .setAutoCancel(true)
      .build()
  }

  /**
   * D-11 목적지 없음 · Way 가 화면 뒤(예: 동승자와 통화 중)라 기본 내비를 직접 못 띄울 때 — 누르면 내비가 열린다.
   * 헤드업으로 뜨는 category 중 navigation 을 쓴다. 1분 뒤엔 지운다(지난 픽업 일).
   */
  fun naviNeeded(context: Context, open: Intent): Notification =
    NotificationCompat.Builder(context, CHANNEL_ASK)
      .setSmallIcon(android.R.drawable.ic_dialog_map)
      .setContentTitle("기본 내비로 연결하기")
      .setContentText("누르면 내비가 열려요. 음성으로 목적지를 설정할 수 있어요.")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
      .setContentIntent(PendingIntent.getActivity(context, NAVI_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
      .setAutoCancel(true)
      .setTimeoutAfter(60_000)
      .build()

  /** 알림을 누르는 건 사용자 동작이라, Way 가 뒤에 있어도 전화 화면이 열린다. */
  private fun dial(context: Context, passenger: Contact): PendingIntent =
    PendingIntent.getActivity(
      context,
      CALL_OFFER_ID,
      PassengerCall.dialIntent(passenger.phone),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun openApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE,
    )
}
