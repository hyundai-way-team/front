package com.kscho.way.gleo

import android.content.Context
import android.content.Intent
import android.util.Log

/** 처리 결과를 Gleo AI 로 돌려보낸다. Gleo 는 7초 안에 응답이 없으면 요청을 취소한다. */
object GleoResponder {
  private const val TAG = "WayGleo"

  fun send(context: Context, reply: GleoReply) {
    val json = reply.toJson()
    val intent =
      Intent(GleoActions.RESPONSE_ACTION).apply {
        // 동승자 이름·ETA 가 들어 있으니 Gleo 앱만 받게 한다
        setPackage(GleoActions.GLEO_PACKAGE)
        putExtra(GleoActions.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(GleoActions.EXTRA_RESPONSE, json)
      }
    context.sendBroadcast(intent)
    Log.i(TAG, "reply ${reply.functionName} ${reply.status.wire}")
    // 응답 본문(동승자 이름 포함)은 켰을 때만: adb shell setprop log.tag.WayGleo DEBUG
    if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, json)
  }
}
