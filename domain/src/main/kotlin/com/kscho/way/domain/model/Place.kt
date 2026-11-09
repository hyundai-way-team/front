package com.kscho.way.domain.model

/** 약속 장소(검색 결과)·지도 위 장소. */
data class Place(val id: String, val name: String, val address: String, val location: LatLng)

/** 핀 위치를 사람이 알아보는 말로. 예) "신촌 XX 빌라 골목 입구 앞" · "신촌로 · 도로 북쪽 인도" */
data class LocationDescription(val title: String, val detail: String)

data class Road(val name: String, val path: List<LatLng>)

/** 지도에 그릴 주변 정보(지도 SDK 를 정하기 전 임시 지도용). */
data class MapFeatures(val roads: List<Road>, val places: List<Place>, val zones: List<RestrictedZone>)
