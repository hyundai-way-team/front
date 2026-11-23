package com.kscho.way.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.pickup.PickupCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GleoActionReceiver : BroadcastReceiver() {
  @Inject lateinit var coordinator: PickupCoordinator

  override fun onReceive(context: Context, intent: Intent) {
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
