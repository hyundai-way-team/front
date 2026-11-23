package com.kscho.way.presentation.component.intent

import android.content.Intent
import android.net.Uri

object PassengerCall {
  fun dialIntent(phone: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
}
