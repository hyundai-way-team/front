package com.kscho.way.data.dataSource.local.history

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPoint
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PickupHistoryFileDataSource(
  private val file: File,
  private val seed: () -> List<PickupRecord> = { emptyList() },
) : PickupHistoryLocalDataSource {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun load(): List<PickupRecord> =
    withContext(Dispatchers.IO) {
      if (!file.exists()) return@withContext seed()
      runCatching { json.decodeFromString<List<StoredPickup>>(file.readText()).map { it.toDomain() } }.getOrDefault(emptyList())
    }

  override suspend fun save(records: List<PickupRecord>) {
    withContext(Dispatchers.IO) {
      file.parentFile?.mkdirs()
      val tmp = File(file.path + TEMP_SUFFIX)
      tmp.writeText(json.encodeToString(records.map { it.toStored() }))
      if (!tmp.renameTo(file)) {
        file.delete()
        tmp.renameTo(file)
      }
    }
  }

  private companion object {
    const val TEMP_SUFFIX = ".tmp"
  }
}

@Serializable
private data class StoredLatLng(val lat: Double, val lng: Double)

@Serializable
private data class StoredPlace(val id: String, val name: String, val address: String, val location: StoredLatLng)

@Serializable
private data class StoredPickup(
  val id: String,
  val passengerId: String,
  val passengerName: String,
  val passengerPhone: String,
  val place: StoredPlace? = null,
  val pickupPoint: StoredLatLng,
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
    place = place?.let { StoredPlace(it.id, it.name, it.address, it.location.toStored()) },
    pickupPoint = pickupPoint.location.toStored(),
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
    place = place?.let { Place(it.id, it.name, it.address, it.location.toDomain()) },
    pickupPoint = PickupPoint(pickupPoint.toDomain(), LocationDescription(pickupTitle, pickupDetail), distanceFromPlaceMeters),
    boarded = boarded,
    pickupLocationChanges = pickupLocationChanges,
    waitedSeconds = waitedSeconds,
    endedAtMillis = endedAtEpochMillis,
  )

private fun LatLng.toStored() = StoredLatLng(latitude, longitude)

private fun StoredLatLng.toDomain() = LatLng(lat, lng)
