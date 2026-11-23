package com.kscho.way.domain.usecase.place

import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.repository.place.PlaceRepository
import javax.inject.Inject

class SearchPlacesUseCase @Inject constructor(
  private val placeRepository: PlaceRepository,
) {
  suspend operator fun invoke(query: String): List<Place> = placeRepository.search(query)
}
