plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
}

android {
    namespace = "com.kscho.way"
    compileSdk = 36
    useLibrary("android.car")
    defaultConfig {
        applicationId = "com.kscho.way"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  implementation(libs.kotlinx.serialization.json)

  implementation(project(":data"))
  implementation(project(":data:device"))
  implementation(project(":presentation"))
  implementation(project(":presentation:component"))
  implementation(libs.retrofit)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.maplibre.android)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

  implementation(libs.pleos.texttospeech)
  implementation(libs.pleos.speechtotext)
  implementation(libs.pleos.fusedlocation)

  constraints {
    implementation(libs.kotlinx.serialization.core) {
      version { strictly(libs.versions.kotlinxSerialization.get()) }
    }
    implementation(libs.kotlinx.serialization.json) {
      version { strictly(libs.versions.kotlinxSerialization.get()) }
    }
  }
}
