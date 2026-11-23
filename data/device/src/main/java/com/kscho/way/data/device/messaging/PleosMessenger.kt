package com.kscho.way.data.device.messaging

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.kscho.way.data.device.launcher.ScreenLauncher
import com.kscho.way.data.sms.PickupTexts
import com.kscho.way.domain.gateway.PassengerMessenger
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.message.PassengerText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PleosMessenger(
  private val screenLauncher: ScreenLauncher,
  private val openComposer: Boolean,
) : PassengerMessenger {
  override suspend fun send(to: Contact, text: PassengerText) {
    val body = PickupTexts.of(text)
    Log.i(TAG, "문자 → ${to.callName}(${to.maskedPhone})\n$body")
    if (!openComposer) return
    withContext(Dispatchers.Main) { screenLauncher.openTextComposer(to, text, composeIntent(to.phone, body)) }
  }

  private fun composeIntent(phone: String, body: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.fromParts(SMS_SCHEME, phone.filter { it.isDigit() || it == '+' }, null))
      .putExtra(SMS_BODY, body)
      .putExtra(Intent.EXTRA_TEXT, body)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  private companion object {
    const val TAG = "WayText"
    const val SMS_SCHEME = "smsto"
    const val SMS_BODY = "sms_body"
  }
}
