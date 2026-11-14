package com.kscho.way.di

/** 시연·개발용 스위치. */
object WayConfig {
  /**
   * true  = 시연용 시뮬레이션 주행. Way 화면에서 D-06~D-10 흐름이 그대로 보인다(시간 20배속).
   * false = Pleos 내비 연결. 픽업까지는 Way 화면(D-06~D-09)이 앞에 있고, 탑승한 뒤에야 내비로 넘어간다.
   */
  const val SIMULATED_DRIVE = false

  /**
   * Way 서버 주소. 지금은 임시 서버(포스크탑 Lightsail 에 함께, deploy/lightsail — 주소는 포스크탑 DuckDNS 이름의 하위 이름).
   * 로컬 서버(./gradlew :server:run)로 시험할 땐 "http://10.0.2.2:8080"(에뮬레이터에서 본 이 컴퓨터).
   * - 서버는 참여 링크를 만들고 차↔동승자 웹을 실시간으로 잇는다. 문자는 차가 보낸다([TEXTS_VIA_PLEOS]).
   * - null 이면 서버 없이 앱 안 mock 서버(동승자 흉내 포함)로 시연한다.
   * 평문 http 는 디버그 빌드에서 10.0.2.2·localhost 만 허용(src/debug/res/xml/network_security_config.xml).
   */
  val SERVER_URL: String? = "https://way.posktop.duckdns.org"

  /**
   * 문자는 차에서 운전자 번호로 보낸다(서버는 링크·실시간 연결만).
   * true  = Pleos 문자 보내기 화면을 문구를 채워 연다(블루투스로 연결된 운전자 휴대폰으로 나간다).
   * false = 시연용: 화면을 띄우지 않고 로그(WayText)만. 휴대폰을 연결할 수 없는 에뮬레이터에선
   *         Pleos 문자가 '기기를 연결하세요' 화면으로 Way 를 가리므로, 화면 흐름 녹화 때만 끈다.
   */
  const val TEXTS_VIA_PLEOS = true

  /**
   * 홈 '최근 픽업'은 앱 파일에 둔다(서버에 묻지 않음). true 면 처음 설치 때(기록 파일이 없을 때) 시연용 지난 픽업 두 건을 넣는다.
   * 실제 배포 땐 false.
   */
  const val DEMO_HISTORY = false
}
