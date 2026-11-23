package com.kscho.way.domain.usecase.vehicle

import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.model.geo.LatLng
import javax.inject.Inject

class GetCarLocationUseCase @Inject constructor(
  private val navigator: Navigator,
) {
  suspend operator fun invoke(): LatLng? = navigator.currentLocation()
}
