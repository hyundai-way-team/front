plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.kscho.way.data.device"
  compileSdk = 36

  defaultConfig {
    minSdk = 28
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(project(":data"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.pleos.vehicle)
  implementation(libs.pleos.navihelper)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
