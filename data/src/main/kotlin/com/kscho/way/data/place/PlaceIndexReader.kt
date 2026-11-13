package com.kscho.way.data.place

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * `tools/map/build_places_index.py` 가 만든 장소 색인 파일(way-places.dat)을 [PlaceIndex] 로 읽는다.
 * 안드로이드를 몰라 [InputStream] 만 받는다 — 앱은 `assets.open("places/way-places.dat")` 를 넘긴다.
 *
 * 형식은 파이썬 쪽 머리말 설명과 짝이다: "WAYP" · 판 · 기준 좌표 · 문자열표 · 종류표 · 동네 · 장소 · 도로.
 * gzip 이든 아니든 읽는다. 파일 이름을 .gz 로 두면 안드로이드 빌드가 풀어서 .gz 를 뗀 이름으로 넣으므로(열 때 못 찾음) .dat 로 둔다.
 */
object PlaceIndexReader {
  const val ASSET_PATH = "places/way-places.dat"
  private val MAGIC = byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'Y'.code.toByte(), 'P'.code.toByte())
  private const val VERSION = 1

  /** [input] 을 끝까지 읽는다(닫지는 않는다). */
  fun read(input: InputStream): PlaceIndex {
    val buffered = BufferedInputStream(input, 64 * 1024)
    buffered.mark(2)
    val b0 = buffered.read()
    val b1 = buffered.read()
    buffered.reset()
    val bytes = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(buffered, 64 * 1024).readBytes() else buffered.readBytes()
    return parse(bytes)
  }

  internal fun parse(bytes: ByteArray): PlaceIndex {
    val r = Cursor(bytes)
    for (b in MAGIC) if (r.byte() != b) throw IOException("장소 색인 파일이 아니다(WAYP 머리말 없음)")
    val version = r.u()
    if (version != VERSION) throw IOException("장소 색인 판 $version 은 못 읽는다(앱은 $VERSION) — build_places_index.py 와 앱을 같이 맞출 것")
    val baseLat = r.u()
    val baseLng = r.u()

    val strings = Array(r.u()) { r.utf8(r.u()) }
    fun str() = strings[r.u()]

    val kindCount = r.u()
    val kindClass = Array(kindCount) { "" }
    val kindSubclass = Array(kindCount) { "" }
    for (i in 0 until kindCount) {
      kindClass[i] = str()
      kindSubclass[i] = str()
    }

    val areaCount = r.u()
    val areaName = Array(areaCount) { "" }
    val areaLevel = Array(areaCount) { AreaLevel.OTHER }
    val areaParent = Array(areaCount) { "" }
    val areaLat = IntArray(areaCount)
    val areaLng = IntArray(areaCount)
    var lat = baseLat
    var lng = baseLng
    for (i in 0 until areaCount) {
      areaName[i] = str()
      areaLevel[i] = AreaLevel.entries.getOrElse(r.u()) { AreaLevel.OTHER }
      areaParent[i] = str()
      lat += r.s()
      lng += r.s()
      areaLat[i] = lat
      areaLng[i] = lng
    }

    val poiCount = r.u()
    val poiName = Array(poiCount) { "" }
    val poiKind = IntArray(poiCount)
    val poiArea = IntArray(poiCount)
    val poiFlags = ByteArray(poiCount)
    val poiLat = IntArray(poiCount)
    val poiLng = IntArray(poiCount)
    lat = baseLat
    lng = baseLng
    for (i in 0 until poiCount) {
      poiName[i] = str()
      poiKind[i] = r.u().also { if (it >= kindCount) throw IOException("장소 종류 번호 $it 가 종류표($kindCount) 밖") }
      poiArea[i] = r.u() - 1
      if (poiArea[i] >= areaCount) throw IOException("동네 번호 ${poiArea[i]} 가 동네($areaCount) 밖")
      poiFlags[i] = r.u().toByte()
      lat += r.s()
      lng += r.s()
      poiLat[i] = lat
      poiLng[i] = lng
    }

    val roadCount = r.u()
    val roadName = Array(roadCount) { "" }
    val roadClass = Array(roadCount) { RoadClass.MINOR }
    val roadStart = IntArray(roadCount + 1)
    var pointLat = IntArray(maxOf(16, roadCount * 4))
    var pointLng = IntArray(pointLat.size)
    var points = 0
    lat = baseLat
    lng = baseLng
    for (i in 0 until roadCount) {
      roadName[i] = str()
      roadClass[i] = RoadClass.entries.getOrElse(r.u()) { RoadClass.MINOR }
      val n = r.u()
      if (points + n > pointLat.size) {
        val size = maxOf(points + n, pointLat.size * 2)
        pointLat = pointLat.copyOf(size)
        pointLng = pointLng.copyOf(size)
      }
      repeat(n) {
        lat += r.s()
        lng += r.s()
        pointLat[points] = lat
        pointLng[points] = lng
        points++
      }
      roadStart[i + 1] = points
    }

    return PlaceIndex(
      areaName = areaName,
      areaLevel = areaLevel,
      areaParent = areaParent,
      areaLat = areaLat,
      areaLng = areaLng,
      poiName = poiName,
      poiKind = poiKind,
      kindClass = kindClass,
      kindSubclass = kindSubclass,
      poiArea = poiArea,
      poiFlags = poiFlags,
      poiLat = poiLat,
      poiLng = poiLng,
      roadName = roadName,
      roadClass = roadClass,
      roadStart = roadStart,
      pointLat = pointLat.copyOf(points),
      pointLng = pointLng.copyOf(points),
    )
  }

  /** LEB128 가변 정수 읽개. */
  private class Cursor(private val b: ByteArray) {
    private var pos = 0

    fun byte(): Byte {
      if (pos >= b.size) throw IOException("장소 색인 파일이 중간에 끊겼다")
      return b[pos++]
    }

    fun u(): Int {
      var result = 0L
      var shift = 0
      while (true) {
        val x = byte().toInt() and 0xFF
        result = result or ((x and 0x7F).toLong() shl shift)
        if (x < 0x80) break
        shift += 7
        if (shift > 35) throw IOException("가변 정수가 너무 길다")
      }
      if (result > Int.MAX_VALUE) throw IOException("가변 정수 $result 가 너무 크다")
      return result.toInt()
    }

    fun s(): Int {
      val v = u()
      return (v ushr 1) xor -(v and 1)
    }

    fun utf8(length: Int): String {
      if (pos + length > b.size) throw IOException("장소 색인 파일이 중간에 끊겼다")
      return String(b, pos, length, Charsets.UTF_8).also { pos += length }
    }
  }
}
