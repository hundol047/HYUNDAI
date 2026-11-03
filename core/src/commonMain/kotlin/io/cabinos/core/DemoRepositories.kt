package io.cabinos.core

class DemoRoadHazardRepository : RoadHazardRepository {
    private val events = mutableListOf<RoadHazard>()
    fun seed(now: Long) {
        if (events.isNotEmpty()) return
        add(RoadHazard("demo-brake", "급감속 신고", "demo-pangyo", 1_500.0, 0.0, .95, .92, 4, now - 60_000, now + 30 * 60_000))
        add(RoadHazard("demo-work", "도로 공사", "demo-pangyo", 4_200.0, 0.0, .7, .86, 2, now - 120_000, now + 60 * 60_000))
        add(RoadHazard("demo-curve", "위험 커브", "demo-pangyo", 7_800.0, 0.0, .6, .95, 3, now - 60_000, now + 60 * 60_000))
    }
    override fun events(nowMs: Long): List<RoadHazard> {
        events.removeAll { it.expiresAtMs <= nowMs }; return events.toList()
    }
    override fun add(event: RoadHazard) { events.removeAll { it.id == event.id }; events.add(event) }
}
class DemoActivityRepository : ActivityRecommendationRepository {
    override fun activities() = listOf(
        NearbyActivity("cafe", "숲속 카페", "카페", 180, 600, "따뜻한 커피와 잠깐의 여유 · 예시 장소", latitude=37.3966,longitude=127.1105),
        NearbyActivity("walk", "그린 산책로", "산책", 120, 600, "가볍게 걷고 기분을 전환하세요 · 예시 장소",latitude=37.3935,longitude=127.1098),
        NearbyActivity("food", "오후의 테이블", "식당", 300, 1200, "여유롭게 즐기는 한 끼 · 예시 장소",latitude=37.3971,longitude=127.1144),
        NearbyActivity("shop", "라이프 스토어", "쇼핑", 420, 900, "일상에 작은 발견 · 예시 장소",latitude=37.3908,longitude=127.1135),
    ).let { originals ->
        originals + originals.flatMap { base -> (2..4).map { index ->
            base.copy(id="${base.id}-$index",name="${base.name} 예시 $index",
                oneWaySeconds=base.oneWaySeconds+(index-1)*120,
                latitude=base.latitude!!+(index-1)*0.0007,longitude=base.longitude!!+(index-1)*0.0005)
        } }
    }
}