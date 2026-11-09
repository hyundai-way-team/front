package com.kscho.way.di

/** 시연·개발용 스위치. */
object WayConfig {
  /**
   * true  = 시연용 시뮬레이션 주행. Way 화면에서 D-06~D-10 흐름이 그대로 보인다(시간 20배속).
   * false = Pleos 내비로 실제 안내(requestRoute). 내비가 앞으로 나와 Way 는 뒤에서 음성·헤드업 알림으로 동작한다.
   */
  const val SIMULATED_DRIVE = true

  /** 서버가 생기기 전까지 mock 서버(메모리)로 시연한다. */
  const val MOCK_SERVER = true
}
