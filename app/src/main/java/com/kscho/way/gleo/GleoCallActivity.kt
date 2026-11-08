package com.kscho.way.gleo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.kscho.way.pickup.PickupSessionStore

/**
 * Gleo 음성 명령 "동승자에게 전화해 줘"(schema.json component="activity").
 * 차량 전화 화면(ACTION_DIAL)을 동승자 번호로 띄우고 바로 닫힌다 — 화면은 없다.
 * 직접 걸기(ACTION_CALL)는 CALL_PHONE 권한이 필요하고 차량에선 전화 기능이 제한될 수 있어 쓰지 않는다.
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
    val session = PickupSessionStore.session.value
    val dial = session?.let { Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it.passengerPhone, null)) }
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
