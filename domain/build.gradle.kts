plugins {
  alias(libs.plugins.kotlin.jvm)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(libs.kotlinx.coroutines.core)
  implementation(libs.hilt.core)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
