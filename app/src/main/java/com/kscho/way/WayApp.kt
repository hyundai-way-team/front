package com.kscho.way

import android.app.Application
import com.kscho.way.di.AppContainer

class WayApp : Application() {
  /** 앱 전체가 함께 쓰는 객체(저장소·픽업 진행). 화면·서비스·Gleo 수신기가 같은 것을 본다. */
  val container: AppContainer by lazy { AppContainer(this) }
}
