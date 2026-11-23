plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.kscho.way.presentation.map"
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
  implementation(project(":presentation:component"))
  implementation(project(":domain"))
  implementation(libs.maplibre.android)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
