package com.kscho.way.data.history

import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.PlaceDto
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPoint
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.pickup.PickupRecords
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 지난 픽업을 앱 파일 하나(JSON)에 둔다 — 서버에 묻지 않는다.
 * 운전자 본인 기록이라 동승자 이름·번호·위치가 서버에 쌓이지 않고, 서버가 안 돼도 홈 '최근 픽업'이 보인다.
 *
 * 파일이 아직 없으면(처음 설치) [seed] 를 쓴다. 파일이 깨졌으면 빈 기록으로 다시 시작한다.
 */
class FilePickupHistory(
  private val file: File,
  private val seed: () -> List<PickupRecord> = { emptyList() },
) : PickupHistoryRepository {
  private val lock = Mutex()
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun recent(limit: Int): List<PickupRecord> = lock.withLock { load().take(limit) }

  override suspend fun save(record: PickupRecord) {
    lock.withLock { write(PickupRecords.add(load(), record)) }
  }

  private suspend fun load(): List<PickupRecord> =
    withContext(Dispatchers.IO) {
      if (!file.exists()) return@withContext seed()
      runCatching { json.decodeFromString<List<StoredPickup>>(file.readText()).map { it.toDomain() } }.getOrDefault(emptyList())
    }

  // 임시 파일에 다 쓴 뒤 바꿔 끼운다 — 쓰다 꺼져도 옛 기록은 남는다
  private suspend fun write(records: List<PickupRecord>) =
    withContext(Dispatchers.IO) {
      file.parentFile?.mkdirs()
      val tmp = File(file.path + ".tmp")
      tmp.writeText(json.encodeToString(records.map { it.toStored() }))
      if (!tmp.renameTo(file)) {
        file.delete()
        tmp.renameTo(file)
      }
    }
}

/** 파일에 쓰는 모양 — 앱 안에서만 쓴다(서버 API 아님). */
@Serializable
private data class StoredPickup(
  val id: String,
  val passengerId: String,
  val passengerName: String,
  val passengerPhone: String,
  val place: PlaceDto? = null,
  val pickupPoint: LatLngDto,
  val pickupTitle: String,
  val pickupDetail: String,
  val distanceFromPlaceMeters: Int? = null,
  val boarded: Boolean,
  val pickupLocationChanges: Int,
  val waitedSeconds: Int? = null,
  val endedAtEpochMillis: Long,
)

private fun PickupRecord.toStored() =
  StoredPickup(
    id = id,
    passengerId = passenger.id,
    passengerName = passenger.name,
    passengerPhone = passenger.phone,
    place = place?.let { PlaceDto(it.id, it.name, it.address, it.location.dto()) },
    pickupPoint = pickupPoint.location.dto(),
    pickupTitle = pickupPoint.description.title,
    pickupDetail = pickupPoint.description.detail,
    distanceFromPlaceMeters = pickupPoint.distanceFromPlaceMeters,
    boarded = boarded,
    pickupLocationChanges = pickupLocationChanges,
    waitedSeconds = waitedSeconds,
    endedAtEpochMillis = endedAtMillis,
  )

private fun StoredPickup.toDomain() =
  PickupRecord(
    id = id,
    passenger = Contact(passengerId, passengerName, passengerPhone),
    place = place?.let { Place(it.id, it.name, it.address, it.location.latLng()) },
    pickupPoint = PickupPoint(pickupPoint.latLng(), LocationDescription(pickupTitle, pickupDetail), distanceFromPlaceMeters),
    boarded = boarded,
    pickupLocationChanges = pickupLocationChanges,
    waitedSeconds = waitedSeconds,
    endedAtMillis = endedAtEpochMillis,
  )

private fun LatLng.dto() = LatLngDto(latitude, longitude)

private fun LatLngDto.latLng() = LatLng(lat, lng)
