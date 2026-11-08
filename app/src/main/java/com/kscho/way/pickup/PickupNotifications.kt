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
  fun ongoing(context: Context, session: PickupSession?): Notification =
    NotificationCompat.Builder(context, CHANNEL_ONGOING)
      .setSmallIcon(android.R.drawable.ic_menu_mylocation)
      .setContentTitle(session?.let { "${it.passengerName}님 픽업 중" } ?: "픽업 중")
      .setContentText(session?.pickupPlaceName)
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
  fun callOffer(context: Context, session: PickupSession): Notification =
    callNotification(context, session, "${session.passengerName}님이 아직 도착하지 않았어요", "전화를 걸까요? '응' 또는 '아니'로 답해 주세요")
      .setOnlyAlertOnce(true)
      .build()

  /** '응'이라고 했지만 Way 가 화면 뒤에 있어 전화 화면을 띄울 수 없을 때 — 다시 헤드업으로 띄워 누르게 한다. */
  fun callTapNeeded(context: Context, session: PickupSession): Notification =
    callNotification(context, session, "${session.passengerName}님에게 전화", "'전화 걸기'를 누르면 전화 화면이 열려요").build()

  private fun callNotification(context: Context, session: PickupSession, title: String, text: String) =
    NotificationCompat.Builder(context, CHANNEL_ASK)
      .setSmallIcon(android.R.drawable.sym_action_call)
      .setContentTitle(title)
      .setContentText(text)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
      .setContentIntent(dial(context, session))
      .addAction(0, "전화 걸기", dial(context, session))
      .setAutoCancel(true)

  /** 알림을 누르는 건 사용자 동작이라, Way 가 뒤에 있어도 전화 화면이 열린다. */
  private fun dial(context: Context, session: PickupSession): PendingIntent =
    PendingIntent.getActivity(
      context,
      CALL_OFFER_ID,
      PassengerCall.dialIntent(session.passengerPhone),
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
