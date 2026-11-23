package com.kscho.way.data.repository.place

import com.kscho.way.data.dataSource.local.place.LocationWording
import com.kscho.way.data.dataSource.local.place.PlaceIndex
import com.kscho.way.data.dataSource.local.place.PlaceIndexReader
import com.kscho.way.data.dataSource.local.place.PlaceSearch
import com.kscho.way.data.dataSource.local.place.PlaceText
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.repository.place.PlaceRepository
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaceRepositoryImpl(
  private val open: () -> InputStream,
  private val origin: suspend () -> LatLng? = { null },
) : PlaceRepository {
  constructor(index: PlaceIndex, origin: suspend () -> LatLng? = { null }) : this(open = { error("색인을 이미 받았다") }, origin = origin) {
    loaded = index
  }

  @Volatile private var loaded: PlaceIndex? = null
  private val loading = Mutex()

  suspend fun warmUp() {
    index()
  }

  override suspend fun search(query: String): List<Place> {
    if (PlaceText.key(query).isEmpty()) return emptyList()
    val index = index()
    val from =
      try {
        origin()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }
    return withContext(Dispatchers.Default) { PlaceSearch.search(index, query, from, MAX_RESULTS) }
  }

  override suspend fun describe(location: LatLng): LocationDescription {
    val index = index()
    return withContext(Dispatchers.Default) { LocationWording.describe(index, location) }
  }

  private suspend fun index(): PlaceIndex =
    loaded
      ?: loading.withLock {
        loaded ?: withContext(Dispatchers.IO) { open().use(PlaceIndexReader::read) }.also { loaded = it }
      }

  companion object {
    const val MAX_RESULTS = 8
  }
}
