package com.kscho.way.gleo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.kscho.way.WayApp
import com.kscho.way.pickup.PassengerCall

/**
 * Gleo 음성 명령 "동승자에게 전화해 줘"(schema.json component="activity").
 * 차량 전화 화면(ACTION_DIAL)을 동승자 번호로 띄우고 바로 닫힌다 — 화면은 없다.
 */
class GleoCallActivity : ComponentActivity() {
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
    val session = (application as WayApp).container.coordinator.state.value?.toGleoSummary()
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
