package com.kscho.way

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import com.kscho.way.theme.WayTheme

class MainActivity : ComponentActivity() {
  private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      WayTheme {
        // 와이어프레임은 1280×720 기준 — 화면 폭에 맞춰 같은 비율로 키운다(Pleos 에뮬 2560×1440 · 160dpi)
        val width = LocalWindowInfo.current.containerSize.width
        val base = LocalDensity.current
        val density = if (width > 0) Density(width / DESIGN_WIDTH_DP, base.fontScale) else base
        CompositionLocalProvider(LocalDensity provides density) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() }
        }
      }
    }
    askNotificationPermission()
  }

  private companion object {
    const val DESIGN_WIDTH_DP = 1280f
  }

  /** 픽업 중 알림(전화 연결 제안 등)을 띄우려면 필요. 운전자 조작이 가능한 출발 전, 앱을 열 때 묻는다. */
  private fun askNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}
