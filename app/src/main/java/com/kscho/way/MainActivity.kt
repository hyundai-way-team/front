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
import com.kscho.way.pickup.PickupService
import com.kscho.way.presentation.MainNavigation
import com.kscho.way.presentation.component.theme.WayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      WayTheme {
        val width = LocalWindowInfo.current.containerSize.width
        val base = LocalDensity.current
        val density = if (width > 0) Density(width / DESIGN_WIDTH_DP, base.fontScale) else base
        CompositionLocalProvider(LocalDensity provides density) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation(onPickupStarted = { PickupService.start(this) }) }
        }
      }
    }
    askNotificationPermission()
  }

  private companion object {
    const val DESIGN_WIDTH_DP = 1280f
  }

  private fun askNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}
