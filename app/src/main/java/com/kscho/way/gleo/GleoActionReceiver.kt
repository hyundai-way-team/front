package com.kscho.way.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kscho.way.WayApp
import com.kscho.way.domain.model.BoardingSource

/**
 * Gleo 의 화면 없는 음성 명령(schema.json component="service" — Gleo 가 sendBroadcast 로 보냄).
 * - GET_PICKUP_STATUS : 픽업 상황 알려줘
 * - COMPLETE_BOARDING : 탑승 완료 (도어 이벤트를 못 쓸 때의 대체 수단)
 *
 * 두 기능 다 파라미터가 없어서 ACTION_PARAM 은 읽지 않는다.
 */
class GleoActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val coordinator = (context.applicationContext as WayApp).container.coordinator
    val before = coordinator.state.value?.toGleoSummary()
    val reply =
      when (intent.action) {
        GleoActions.GET_PICKUP_STATUS -> PickupGleoReplies.status(before)
        GleoActions.COMPLETE_BOARDING -> {
          if (before != null && !before.boarded) coordinator.confirmBoarded(BoardingSource.VOICE)
          PickupGleoReplies.boarding(before)
        }
        else -> return
      }
    GleoResponder.send(context, reply)
  }
}
