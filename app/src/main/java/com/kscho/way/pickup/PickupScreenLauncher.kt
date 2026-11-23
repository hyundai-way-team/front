package com.kscho.way.pickup

import android.app.Notification
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kscho.way.data.device.launcher.ScreenLauncher
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.message.PassengerText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PickupScreenLauncher @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : ScreenLauncher {
  override fun openNavi(intent: Intent) {
    if (WayScreen.isVisible(context)) {
      val opened = runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "기본 내비를 열지 못했어요 — 알림으로 남김", it) }.isSuccess
      if (opened) return
    }
    notify(PickupNotifications.NAVI_ID, PickupNotifications.naviNeeded(context, intent))
  }

  override fun openTextComposer(to: Contact, text: PassengerText, intent: Intent) {
    if (WayScreen.isVisible(context)) {
      try {
        context.startActivity(intent)
        return
      } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "문자 보내기 화면이 없어요 — 알림으로 남김", e)
      }
    }
    notify(PickupNotifications.textId(text), PickupNotifications.textNeeded(context, to, text, intent))
  }

  private fun notify(id: Int, notification: Notification) {
    PickupNotifications.createChannels(context)
    context.getSystemService(NotificationManager::class.java).notify(id, notification)
  }

  private companion object {
    const val TAG = "WayScreen"
  }
}
