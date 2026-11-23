package com.kscho.way.data.dataSource.local.place

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.Place
internal object PlaceSearch {
  private const val SAME_PLACE_METERS = 150.0

  private class Candidate(val area: Boolean, val index: Int, val tier: Int, val score: Double)

  fun search(index: PlaceIndex, query: String, from: LatLng?, limit: Int): List<Place> {
    val q = PlaceText.key(query)
    if (q.isEmpty() || limit <= 0) return emptyList()
    val terms = query.trim().split(Regex("\\s+")).map(PlaceText::key).filter { it.isNotEmpty() }.distinct()
    val multi = terms.size >= 2

    val fromLat = from?.let { e6(it.latitude) }
    val fromLng = from?.let { e6(it.longitude) }
    val cosLat = fromLat?.let(::cosOf) ?: 1.0

    fun score(lat: Int, lng: Int, weight: Double, nameLength: Int): Double =
      if (fromLat != null && fromLng != null) {
        meters(fromLat, fromLng, lat, lng, cosLat) * weight
      } else {
        weight * 1_000 + nameLength
      }

    fun tier(name: String, address: String): Int =
      when {
        name == q -> 0
        name.startsWith(q) -> 1
        name.contains(q) -> 2
        address.contains(q) -> 3
        multi && terms.all { name.contains(it) || address.contains(it) } -> 4
        else -> -1
      }

    val candidates = ArrayList<Candidate>()

    for (i in 0 until index.areaCount) {
      val t = tier(index.areaKey[i], index.areaParentKey[i])
      if (t < 0) continue
      candidates += Candidate(true, i, t, score(index.areaLat[i], index.areaLng[i], EASY_WEIGHT, index.areaName[i].length))
    }

    val areaHit = BooleanArray(index.areaCount) { index.areaAddressKey[it].contains(q) }
    val areaTerms = if (multi) Array(index.areaCount) { a -> terms.filter { index.areaAddressKey[a].contains(it) }.toSet() } else null
    for (i in 0 until index.poiCount) {
      val name = index.poiKey[i]
      val area = index.poiArea[i]
      val t =
        when {
          name == q -> 0
          name.startsWith(q) -> 1
          name.contains(q) -> 2
          area >= 0 && areaHit[area] -> 3
          multi && terms.all { name.contains(it) || (area >= 0 && it in areaTerms!![area]) } -> 4
          else -> continue
        }
      val weight =
        when (index.poiKindOf(i)) {
          PoiKind.STATION,
          PoiKind.MAJOR -> EASY_WEIGHT
          PoiKind.BUS_STOP,
          PoiKind.OTHER -> 1.0
        } * (if (index.poiStreetLevel(i)) 1.0 else INDOOR_WEIGHT)
      candidates += Candidate(false, i, t, score(index.poiLat[i], index.poiLng[i], weight, index.poiName[i].length))
    }

    candidates.sortWith(compareBy<Candidate>({ it.tier }, { it.score }))

    val picked = ArrayList<Candidate>(limit)
    for (c in candidates) {
      if (picked.size >= limit) break
      val name = nameOf(index, c)
      val lat = latOf(index, c)
      val lng = lngOf(index, c)
      val dup =
        picked.any { p -> nameOf(index, p) == name && meters(lat, lng, latOf(index, p), lngOf(index, p), cosOf(lat)) <= SAME_PLACE_METERS }
      if (!dup) picked += c
    }
    return picked.map { c ->
      if (c.area) {
        Place(
          id = "area:${c.index}",
          name = index.areaName[c.index],
          address = index.areaParent[c.index].ifEmpty { index.areaName[c.index] },
          location = latLng(index.areaLat[c.index], index.areaLng[c.index]),
        )
      } else {
        Place(
          id = "poi:${c.index}",
          name = index.poiName[c.index],
          address = index.poiAddress(c.index),
          location = latLng(index.poiLat[c.index], index.poiLng[c.index]),
        )
      }
    }
  }

  private const val EASY_WEIGHT = 0.6

  private const val INDOOR_WEIGHT = 1.5

  private fun nameOf(index: PlaceIndex, c: Candidate) = if (c.area) index.areaName[c.index] else index.poiName[c.index]

  private fun latOf(index: PlaceIndex, c: Candidate) = if (c.area) index.areaLat[c.index] else index.poiLat[c.index]

  private fun lngOf(index: PlaceIndex, c: Candidate) = if (c.area) index.areaLng[c.index] else index.poiLng[c.index]
}
