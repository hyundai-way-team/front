package com.kscho.way.data.place

import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.repository.PlaceRepository
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 앱 속 OpenStreetMap 장소 색인으로 하는 장소 검색·핀 위치 설명 — 외부 지도·검색 API 를 부르지 않는다.
 *
 * 앱에서 잇기:
 * ```
 * OfflinePlaceRepository(
 *   open = { context.assets.open(PlaceIndexReader.ASSET_PATH) },
 *   origin = { navigator.currentLocation() },  // 검색 결과를 가까운 순으로
 * )
 * ```
 * 색인(약 3 MB, 풀면 5 MB · 장소 13만)은 처음 쓸 때 한 번 읽는다(IO 스레드). 미리 읽으려면 [warmUp].
 *
 * @param origin 검색 기준 위치(보통 차 위치). null 이거나 실패하면 알아보기 쉬운 곳 → 짧은 이름 순.
 */
class OfflinePlaceRepository(
  private val open: () -> InputStream,
  private val origin: suspend () -> LatLng? = { null },
) : PlaceRepository {
  /** 이미 읽은 색인으로(검사·미리 읽어 둔 것). */
  constructor(index: PlaceIndex, origin: suspend () -> LatLng? = { null }) : this(open = { error("색인을 이미 받았다") }, origin = origin) {
    loaded = index
  }

  @Volatile private var loaded: PlaceIndex? = null
  private val loading = Mutex()

  /** 색인을 미리 읽어 둔다(앱 시작 때 뒤에서 불러 두면 첫 검색이 빠르다). */
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
