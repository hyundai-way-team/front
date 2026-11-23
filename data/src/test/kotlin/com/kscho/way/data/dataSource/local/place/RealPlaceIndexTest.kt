package com.kscho.way.data.dataSource.local.place

import com.kscho.way.data.repository.place.PlaceRepositoryImpl
import com.kscho.way.domain.model.geo.LatLng
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RealPlaceIndexTest {
  private val file = listOf("../app/src/main/assets/places/way-places.dat", "app/src/main/assets/places/way-places.dat").map(::File).firstOrNull { it.exists() }

  private val sinchon = LatLng(37.5567, 126.9420)

  private fun inSeoul(p: LatLng) = p.latitude in 37.41..37.72 && p.longitude in 126.75..127.20

  @Test
  fun searchAndDescribe_onRealIndex() = runTest {
    assumeTrue("장소 색인 파일이 없다 — tools/map/build_places_index.py", file != null)
    val started = System.nanoTime()
    val index = file!!.inputStream().use(PlaceIndexReader::read)
    println("색인 읽기 ${(System.nanoTime() - started) / 1_000_000} ms · 장소 ${index.poiCount} · 동네 ${index.areaCount} · 도로 ${index.roadCount}")
    val repo = PlaceRepositoryImpl(index, origin = { sinchon })

    for (q in listOf("연세대", "신촌역", "홍대입구", "이대", "연남동", "현대백화점", "스타벅스")) {
      val t = System.nanoTime()
      val result = repo.search(q)
      println("검색 '$q' (${(System.nanoTime() - t) / 1_000_000} ms)")
      result.forEach { println("  ${it.name} | ${it.address} | ${it.location.latitude}, ${it.location.longitude}") }
      assertTrue("'$q' 결과 없음", result.isNotEmpty())
      assertTrue("'$q' 결과가 서울 밖", result.all { inSeoul(it.location) })
    }
    assertTrue(repo.search("연세대").first().name.startsWith("연세대"))
    assertEquals("신촌역", repo.search("신촌역").first().name)
    assertTrue(repo.search("홍대입구").first().name.startsWith("홍대입구"))
    assertTrue(repo.search("신촌역").first().address.startsWith("서울 "))

    for (p in listOf(LatLng(37.556700, 126.942000), LatLng(37.556800, 126.942600), LatLng(37.556600, 126.942700), LatLng(37.556440, 126.943500))) {
      val d = repo.describe(p)
      println("핀 ${p.latitude}, ${p.longitude} → ${d.title} / ${d.detail}")
    }
    assertTrue(repo.describe(LatLng(37.556700, 126.942000)).detail.startsWith("신촌로"))
  }
}
