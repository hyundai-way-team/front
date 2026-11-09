plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kscho.way"
    compileSdk = 36
    // AAOS Car API(CarUxRestrictionsManager 주행 규제 등)를 컴파일에 보이게
    useLibrary("android.car")
    defaultConfig {
        applicationId = "com.kscho.way"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // 차량(Pleos)·M1 에뮬은 arm64, PC 에뮬용 x86_64 — 지도 엔진(.so)이 ABI 마다 13MB 라 나머지는 뺀다
        ndk {
          abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    // 오프라인 지도 파일은 압축하지 않아야 MapLibre 가 필요한 부분만 바로 읽는다(pmtiles://asset://)
    androidResources {
      noCompress += "pmtiles"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Gleo 응답 JSON
  implementation(libs.kotlinx.serialization.json)

  // 클린 아키텍처 모듈 — 화면은 domain 을 보고, 구현(mock 서버·저장소)은 data 에서 주입한다
  implementation(project(":data"))
  implementation(libs.kotlinx.coroutines.android)

  // 지도: MapLibre + 앱에 넣은 OpenStreetMap 지도(assets/map, tools/map/build_offline_map.py 로 만든다)
  implementation(libs.maplibre.android)

  // Pleos Connect SDK — 쓰지 않을 SDK 는 지우고, 쓰는 API 의 권한만 Manifest 에 선언
  implementation(libs.pleos.vehicle)
  implementation(libs.pleos.navihelper)
  implementation(libs.pleos.texttospeech)
  implementation(libs.pleos.speechtotext)
  implementation(libs.pleos.fusedlocation)
}
