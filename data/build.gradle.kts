// 데이터: 도메인 저장소 구현 + 서버 API(WayApi) 명세 + 서버 연동 전 mock 구현.
// 서버가 생기면 WayApi 의 실제 구현(HTTP)만 추가하고 화면·도메인은 그대로 둔다.
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

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
