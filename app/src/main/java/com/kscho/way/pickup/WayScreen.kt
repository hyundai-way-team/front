package com.kscho.way.pickup

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Way 화면이 지금 보이는지(분할 화면 한쪽 포함).
 * 안드로이드는 화면에 안 보이는 앱이 다른 화면(전화·문자)을 띄우는 걸 말없이 막는다 — 포그라운드 서비스여도.
 * 안 보이면 헤드업 알림 버튼으로 대신한다(알림을 누르는 건 사용자 동작이라 열린다).
 */
object WayScreen {
  fun isVisible(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true // Pleos 는 API 34 — 옛 OS 는 그냥 시도
    return context.getSystemService(ActivityManager::class.java).appTasks.any {
      runCatching { it.taskInfo.isVisible }.getOrDefault(false)
    }
  }
}
