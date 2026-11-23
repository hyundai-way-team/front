plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.kscho.way.presentation.home"
  compileSdk = 36

  defaultConfig {
    minSdk = 28
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
  implementation(project(":presentation:component"))
  implementation(project(":presentation:map"))
  implementation(project(":domain"))

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
