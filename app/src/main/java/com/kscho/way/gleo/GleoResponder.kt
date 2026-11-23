package com.kscho.way.gleo

import android.content.Context
import android.content.Intent
import android.util.Log

object GleoResponder {
  private const val TAG = "WayGleo"

  fun send(context: Context, reply: GleoReply) {
    val json = reply.toJson()
    val intent =
      Intent(GleoActions.RESPONSE_ACTION).apply {
        setPackage(GleoActions.GLEO_PACKAGE)
        putExtra(GleoActions.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(GleoActions.EXTRA_RESPONSE, json)
      }
    context.sendBroadcast(intent)
    Log.i(TAG, "reply ${reply.functionName} ${reply.status.wire}")
    if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, json)
  }
}
