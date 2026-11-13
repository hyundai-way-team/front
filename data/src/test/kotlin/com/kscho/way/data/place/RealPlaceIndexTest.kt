package com.kscho.way.data.place

import com.kscho.way.domain.model.LatLng
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 앱에 넣은 실제 색인(app/src/main/assets/places/way-places.dat)으로 몇 가지 검색·핀 설명을 해 본다.
 * 파일이 없으면(색인을 아직 안 만듦) 건너뛴다. 결과는 표준 출력에도 찍는다(테스트 보고서 XML 의 system-out).
 */
class RealPlaceIndexTest {
  private val file = listOf("../app/src/main/assets/places/way-places.dat", "app/src/main/assets/places/way-places.dat").map(::File).firstOrNull { it.exists() }

  /** 시연 출발지 근처(신촌로 · 아현 쪽) */
  private val sinchon = LatLng(37.5567, 126.9420)

  private fun inSeoul(p: LatLng) = p.latitude in 37.41..37.72 && p.longitude in 126.75..127.20

  @Test
  fun searchAndDescribe_onRealIndex() = runTest {
    assumeTrue("장소 색인 파일이 없다 — tools/map/build_places_index.py", file != null)
    val started = System.nanoTime()
    val index = file!!.inputStream().use(PlaceIndexReader::read)
    println("색인 읽기 ${(System.nanoTime() - started) / 1_000_000} ms · 장소 ${index.poiCount} · 동네 ${index.areaCount} · 도로 ${index.roadCount}")
    val repo = OfflinePlaceRepository(index, origin = { sinchon })

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

    // 신촌로 위 몇 점: 빌라 골목 입구(북쪽 인도) · 버스정류장 옆 · 두 차로 사이 · 남쪽 인도
    for (p in listOf(LatLng(37.556700, 126.942000), LatLng(37.556800, 126.942600), LatLng(37.556600, 126.942700), LatLng(37.556440, 126.943500))) {
      val d = repo.describe(p)
      println("핀 ${p.latitude}, ${p.longitude} → ${d.title} / ${d.detail}")
    }
    assertTrue(repo.describe(LatLng(37.556700, 126.942000)).detail.startsWith("신촌로"))
  }
}
