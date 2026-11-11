package com.kscho.way.domain.model

/** 약속 장소(검색 결과). 지도의 도로·장소는 앱 속 오프라인 지도에 있다. */
data class Place(val id: String, val name: String, val address: String, val location: LatLng)

/** 핀 위치를 사람이 알아보는 말로. 예) "신촌 XX 빌라 골목 입구 앞" · "신촌로 · 도로 북쪽 인도" */
data class LocationDescription(val title: String, val detail: String)
