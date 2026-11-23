package com.kscho.way.data.place

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.place.Place
/**
 * 장소 검색 순위 — 공백·기호·대소문자는 무시한다.
 *
 * 1. 이름이 검색어와 같다
 * 2. 이름이 검색어로 시작한다
 * 3. 이름에 검색어가 들어 있다
 * 4. 주소(서울 마포구 연남동)에 검색어가 들어 있다
 * 5. 검색어가 여러 낱말이면 낱말마다 이름이나 주소에 있다("연남동 카페")
 *
 * 같은 단계 안에서는 기준 위치(차 위치)에서 가까운 순 — 역·대학·병원·백화점·동네 이름처럼 알아보기 쉬운 곳은 조금 당기고,
 * 실내·지하·2층 이상은 조금 민다.
 * 기준 위치를 모르면 알아보기 쉬운 곳 → 짧은 이름 순. 이름이 같고 150 m 안이면(길 양쪽 정류장 등) 하나만 보인다.
 */
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

    // 시·구·동 이름 — 주소는 윗주소("서울 마포구")
    for (i in 0 until index.areaCount) {
      val t = tier(index.areaKey[i], index.areaParentKey[i])
      if (t < 0) continue
      candidates += Candidate(true, i, t, score(index.areaLat[i], index.areaLng[i], EASY_WEIGHT, index.areaName[i].length))
    }

    // 장소 — 주소는 동 주소. 동마다 한 번만 비교하려고 먼저 동 주소가 맞는지 표시해 둔다.
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
          // 색인 번호로 — 좌표만으로는 한 건물 안 가게들이 겹친다(화면 목록 key)
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

  /** 알아보기 쉬운 곳(역·대학·병원·동네 이름 …)은 거리를 이만큼 줄여 셈한다. */
  private const val EASY_WEIGHT = 0.6

  /** 실내·지하·2층 이상(차를 대고 만나기 어려운 곳)은 조금 밀어낸다. */
  private const val INDOOR_WEIGHT = 1.5

  private fun nameOf(index: PlaceIndex, c: Candidate) = if (c.area) index.areaName[c.index] else index.poiName[c.index]

  private fun latOf(index: PlaceIndex, c: Candidate) = if (c.area) index.areaLat[c.index] else index.poiLat[c.index]

  private fun lngOf(index: PlaceIndex, c: Candidate) = if (c.area) index.areaLng[c.index] else index.poiLng[c.index]
}
