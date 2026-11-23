package com.kscho.way.gleo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.pickup.PassengerCall
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GleoCallActivity : ComponentActivity() {
  @Inject lateinit var coordinator: PickupCoordinator

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handle(intent)
    finish()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handle(intent)
    finish()
  }

  private fun handle(intent: Intent) {
    if (intent.action != GleoActions.CALL_PASSENGER) return
    val session = coordinator.state.value?.toGleoSummary()
    val dial = session?.let { PassengerCall.dialIntent(it.passengerPhone) }
    val reply = PickupGleoReplies.call(session, dialerAvailable = dial?.resolveActivity(packageManager) != null)
    if (reply.status == GleoStatus.SUCCESS && dial != null) {
      try {
        startActivity(dial)
      } catch (_: ActivityNotFoundException) {
        GleoResponder.send(this, PickupGleoReplies.call(session, dialerAvailable = false))
        return
      }
    }
    GleoResponder.send(this, reply)
  }
}
