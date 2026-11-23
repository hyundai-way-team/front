package com.kscho.way.pickup

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.kscho.way.data.sms.PickupTexts
import com.kscho.way.di.WayConfig
import com.kscho.way.domain.gateway.PassengerMessenger
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.message.PassengerText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 차에서 운전자 번호로 동승자에게 문자를 보낸다 — Pleos 문자(ai.umos.callandmessage).
 *
 * 차에는 SIM 이 없고, Pleos 전화·메시지 앱이 블루투스로 연결된 운전자 휴대폰을 통해 보낸다(에뮬에서 확인:
 * SENDTO smsto: 를 받는 SendMessageOverlayActivity, 휴대폰이 없으면 '기기를 연결하세요').
 * 앱이 몰래 보내는 공개 API 는 없어 문구를 채운 보내기 화면을 연다 — 실제 보내기는 운전자가 확인한다(실차 확인 필요).
 *
 * Way 가 화면 뒤에 있으면(Pleos 내비가 앞) 화면을 직접 못 띄워 헤드업 알림 '문자 보내기'로 대신한다.
 */
class PleosMessenger(
  private val context: Context,
  /** false = 시연용: 보내기 화면을 띄우지 않고 로그만 남긴다([WayConfig.TEXTS_VIA_PLEOS]). */
  private val openPleos: Boolean = WayConfig.TEXTS_VIA_PLEOS,
) : PassengerMessenger {
  override suspend fun send(to: Contact, text: PassengerText) {
    val body = PickupTexts.of(text)
    Log.i(TAG, "문자 → ${to.callName}(${to.maskedPhone})\n$body")
    if (!openPleos) return
    val intent = composeIntent(to.phone, body)
    withContext(Dispatchers.Main) {
      if (WayScreen.isVisible(context)) {
        try {
          context.startActivity(intent)
          return@withContext
        } catch (e: ActivityNotFoundException) {
          Log.w(TAG, "문자 보내기 화면이 없어요 — 알림으로 남김", e)
        }
      }
      PickupNotifications.createChannels(context)
      context
        .getSystemService(NotificationManager::class.java)
        .notify(PickupNotifications.textId(text), PickupNotifications.textNeeded(context, to, text, intent))
    }
  }

  companion object {
    private const val TAG = "WayText"

    /** 받는 사람 번호와 문구를 채운 문자 보내기 화면(SENDTO smsto:). 문구는 sms_body(관례)와 EXTRA_TEXT 둘 다 넣는다. */
    fun composeIntent(phone: String, body: String): Intent =
      Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone.filter { it.isDigit() || it == '+' }, null))
        .putExtra("sms_body", body)
        .putExtra(Intent.EXTRA_TEXT, body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}
