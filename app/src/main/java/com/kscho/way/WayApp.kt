package com.kscho.way

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WayApp : Application() {
  @Inject lateinit var startup: WayStartup

  override fun onCreate() {
    super.onCreate()
    startup.run()
  }
}
