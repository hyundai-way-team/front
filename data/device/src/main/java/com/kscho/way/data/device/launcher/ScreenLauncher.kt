package com.kscho.way.data.device.launcher

import android.content.Intent
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.message.PassengerText

interface ScreenLauncher {
  fun openNavi(intent: Intent)

  fun openTextComposer(to: Contact, text: PassengerText, intent: Intent)
}
