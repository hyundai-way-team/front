package com.kscho.way

import android.util.Log
import com.kscho.way.data.repository.place.PlaceRepositoryImpl
import com.kscho.way.di.ApplicationScope
import com.kscho.way.vehicle.CarLocationTracker
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WayStartup @Inject constructor(
  private val carLocation: CarLocationTracker,
  private val offlinePlaces: PlaceRepositoryImpl,
  @param:ApplicationScope private val scope: CoroutineScope,
) {
  fun run() {
    carLocation.start()
    scope.launch {
      runCatching { offlinePlaces.warmUp() }.onFailure { Log.w(TAG, "장소 색인을 읽지 못함", it) }
    }
  }

  private companion object {
    const val TAG = "WayPlaces"
  }
}
