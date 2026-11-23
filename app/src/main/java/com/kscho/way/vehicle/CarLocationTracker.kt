package com.kscho.way.vehicle

import com.kscho.way.di.ApplicationScope
import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.model.geo.LatLng
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class CarLocationTracker @Inject constructor(
  private val navigator: Navigator,
  @param:ApplicationScope private val scope: CoroutineScope,
) {
  @Volatile
  var last: LatLng? = null
    private set

  fun start() {
    scope.launch {
      while (isActive) {
        runCatching { navigator.currentLocation() }.getOrNull()?.let { last = it }
        delay(REFRESH_MILLIS)
      }
    }
  }

  private companion object {
    const val REFRESH_MILLIS = 30_000L
  }
}
