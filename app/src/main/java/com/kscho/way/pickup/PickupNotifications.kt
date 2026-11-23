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
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.message.PassengerText
import com.kscho.way.presentation.component.intent.PassengerCall
object PickupNotifications {
  const val ONGOING_ID = 1001
  const val CALL_OFFER_ID = 1002
  const val NAVI_ID = 1003
  private const val TEXT_ID_BASE = 1010

  fun textId(text: PassengerText): Int =
    TEXT_ID_BASE +
      when (text) {
        is PassengerText.Departure -> 0
        is PassengerText.EtaChanged -> 1
        is PassengerText.ArrivingSoon -> 2
      }

  val TEXT_IDS = TEXT_ID_BASE..TEXT_ID_BASE + 2

  private const val EXTRA_NO_HEADS_UP = "exclude_from_heads_up_notification"
  private const val CHANNEL_ONGOING = "pickup_ongoing"
  private const val CHANNEL_ASK = "pickup_ask"

  fun createChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ONGOING, "픽업 진행", NotificationManager.IMPORTANCE_LOW)
    )
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ASK, "픽업 음성 질문", NotificationManager.IMPORTANCE_HIGH).apply {
        setSound(null, null)
        enableVibration(false)
      }
    )
  }

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

  fun callOffer(context: Context, passenger: Contact): Notification =
    callNotification(context, passenger, "${passenger.callName} 님이 아직 도착하지 않았어요", "전화를 걸까요? '응' 또는 '아니'로 답해 주세요")
      .setOnlyAlertOnce(true)
      .build()

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
