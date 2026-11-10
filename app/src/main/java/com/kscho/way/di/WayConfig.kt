package com.kscho.way.di

/** 시연·개발용 스위치. */
object WayConfig {
  /**
   * true  = 시연용 시뮬레이션 주행. Way 화면에서 D-06~D-10 흐름이 그대로 보인다(시간 20배속).
   * false = Pleos 내비로 실제 안내(requestRoute). 내비가 앞으로 나와 Way 는 뒤에서 음성·헤드업 알림으로 동작한다.
   */
  const val SIMULATED_DRIVE = true

  /**
   * Way 서버 주소. 로컬 서버(./gradlew :server:run)를 에뮬레이터에서 보면 10.0.2.2 가 이 컴퓨터다.
   * - 서버는 참여 링크를 만들고 차↔동승자 웹을 실시간으로 잇는다. 문자는 차가 보낸다([TEXTS_VIA_PLEOS]).
   * - null 이면 서버 없이 앱 안 mock 서버(동승자 흉내 포함)로 시연한다.
   * 평문 http 는 디버그 빌드에서 10.0.2.2·localhost 만 허용(src/debug/res/xml/network_security_config.xml).
   */
  val SERVER_URL: String? = "http://10.0.2.2:8080"

  /**
   * 문자는 차에서 운전자 번호로 보낸다(서버는 링크·실시간 연결만).
   * true  = Pleos 문자 보내기 화면을 문구를 채워 연다(블루투스로 연결된 운전자 휴대폰으로 나간다).
   * false = 시연용: 화면을 띄우지 않고 로그(WayText)만. 휴대폰을 연결할 수 없는 에뮬레이터에선
   *         Pleos 문자가 '기기를 연결하세요' 화면으로 Way 를 가리므로, 화면 흐름 녹화 때만 끈다.
   */
  const val TEXTS_VIA_PLEOS = true
}
