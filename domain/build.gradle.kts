// 도메인: 픽업 규칙·모델·저장소 인터페이스. 안드로이드·서버·Pleos 를 모른다(순수 Kotlin).
plugins {
  alias(libs.plugins.kotlin.jvm)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
