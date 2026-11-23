package com.kscho.way.data.history

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.response.PlaceResponse
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

@Serializable
private data class StoredPickup(
  val id: String,
  val passengerId: String,
  val passengerName: String,
  val passengerPhone: String,
  val place: PlaceResponse? = null,
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
    place = place?.let { PlaceResponse(it.id, it.name, it.address, it.location.dto()) },
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
