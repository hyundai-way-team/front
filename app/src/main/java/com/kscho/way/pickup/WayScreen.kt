package com.kscho.way.pickup

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object WayScreen {
  fun isVisible(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(ActivityManager::class.java).appTasks.any {
      runCatching { it.taskInfo.isVisible }.getOrDefault(false)
    }
  }
}
