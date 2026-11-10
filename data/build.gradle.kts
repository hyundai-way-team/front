// 데이터: 도메인 저장소 구현 + 서버 API(WayApi) 명세 + 서버 연동 전 mock 구현.
// 서버 연동은 WayApi 의 HTTP 구현(HttpWayApi)만 갈아 끼우고 화면·도메인은 그대로 둔다.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(project(":domain"))
  implementation(libs.kotlinx.serialization.json)
  // 서버 연동(HttpWayApi) — HTTP + 동승자 상태 WebSocket
  implementation(libs.okhttp)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
