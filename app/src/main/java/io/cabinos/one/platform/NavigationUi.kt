package io.cabinos.one.platform

import io.cabinos.core.*

enum class GpsConnection {
    OFF,
    WAITING,
    READY,
    POOR,
    STALE,
    DENIED,
    DISABLED,
}

data class NavigationUi(
    val guidance: Guidance = Guidance(),
    val places: List<NavPlace> = emptyList(),
    val busy: Boolean = false,
    val selectedPlace: NavPlace? = null,
    val hasMore: Boolean = false,
    val originAccessMeters: Double = 0.0,
    val originReady: Boolean = false,
    val notice: String = "목적지를 검색하거나 위도,경도를 입력하세요.",
    val gpsNotice: String = "GPS 미연결 · 내 위치 사용을 눌러 주세요.",
    val gpsConnection: GpsConnection = GpsConnection.OFF,
    val gpsAccuracy: Double? = null,
    val mapFix: NavFix? = null,
    val gpsAgeSeconds: Long? = null,
    val language: String = "ko-KR",
    val voiceListening: Boolean = false,
    val voiceNotice: String = "목적지 → 결과 번호를 말하세요.",
    val searchQuery: String = "",
    val roadSafety: RoadSafetyUi = RoadSafetyUi(),
    val echoRisks: List<EchoRisk> = emptyList(),
    val echoNotice: String = "EchoRoad 공유 서버 미연결",
    val echoAhead: String? = null,
    val traffic: TrafficFlow? = null,
    val trafficNotice: String = "실시간 교통 미연결",
)
