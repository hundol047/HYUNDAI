package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class PlaceSearchTest {
    @Test fun globalNamesakesPreferFreshOriginWithoutExcludingOtherCountries() {
        val center=NavPoint(37.3948,127.1112)
        val near=NavPlace("판교역",center)
        val far=NavPlace("판교",NavPoint(38.5,126.0))
        assertEquals(listOf(near,far),PlaceSearch.results(listOf(far,near),center,false))
        val request=PlaceSearch.requests("판교역",center).single()
        assertEquals("0",request["bounded"]);assertTrue(request.containsKey("viewbox"))
        assertFalse(request.containsKey("countrycodes"))
    }

    @Test fun userPickedMapPointsWrapWorldCopiesAndNeverInventBusinessMetadata() {
        val point=PlaceSearch.mapPoint(-16.5,180.2)!!
        assertEquals(-179.8,point.point.longitude,0.00001)
        assertTrue(point.name.contains("직접 고른"));assertTrue(point.source.contains("미확인"));assertEquals("",point.address)
        assertNull(PlaceSearch.mapPoint(91.0,0.0));assertNull(PlaceSearch.mapPoint(0.0,Double.NaN))
    }
    @Test fun namesakeLocationHintExposesDistantResultsWithoutRemovingGlobalSearchResults() {
        val origin=NavPoint(37.6868,128.7550)
        val restaurant=PlaceSearch.place("대관령 옛길",37.5073769,127.1056803,"namesake",category="식당")!!
        assertTrue(PlaceSearch.locationHint(origin,restaurant.point).contains("146.7 km"))
        assertTrue(PlaceSearch.locationHint(origin,restaurant.point).contains("먼 결과"))
        assertEquals(listOf(restaurant),PlaceSearch.results(listOf(restaurant),origin,false))
        assertEquals("",PlaceSearch.locationHint(null,restaurant.point))
        assertEquals("",PlaceSearch.locationHint(NavPoint(Double.NaN,0.0),restaurant.point))
        assertFalse(PlaceSearch.locationHint(restaurant.point,restaurant.point).contains("먼 결과"))
    }
    @Test fun spacingRetryNeverTruncatesTheBranchAtTheRequestLengthBoundary() {
        val query="가".repeat(175)+" 원주무실"
        assertEquals(180,query.length);assertNull(PlaceSearch.spacingSuggestion(query))
        val shorter=query.drop(1);val suggestion=PlaceSearch.spacingSuggestion(shorter)!!
        assertEquals(180,suggestion.length)
        assertEquals(suggestion,PlaceSearch.requests(suggestion).single()["q"])
        assertTrue(suggestion.endsWith("원주 무실"))
    }
    @Test fun spacingRetryPreservesBusinessCityAndBranchWithoutBroadeningTheQuery() {
        assertEquals("스타벅스 원주 무실",PlaceSearch.spacingSuggestion("스타벅스 원주무실"))
        assertEquals("카페 서울 강남",PlaceSearch.spacingSuggestion("카페 서울강남"))
        listOf("스타벅스 원주 무실","원주무실","서울우유","연세대학교 미래캠퍼스","Café de Flore Paris","카페 서울숲","원주시 연세대길 1").forEach {assertNull(PlaceSearch.spacingSuggestion(it))}
    }
    @Test fun outsideCircleResultsAreStillExcludedWhenFetchingTheNextPage() {
        val origin=NavPoint(37.5,127.0);val places=(1..6).map {PlaceSearch.place("far shop $it",37.68,127.2,"$it")!!}
        val page=PlaceSearch.page(places,origin,true,emptyList())
        assertTrue(page.places.isEmpty());assertTrue(page.hasMore);assertEquals(6,page.seenIds.size)
        assertEquals("1,2,3,4,5,6",PlaceSearch.requests("shop",origin,true,page.seenIds).first()["exclude_place_ids"])
    }
    @Test fun worldSearchAcceptsKoreanBranchesAndDoesNotAddACountryFilter() {
        val p=PlaceSearch.requests("  스타벅스 판교  ").single()
        assertEquals("스타벅스 판교",p["q"]);assertEquals("1",p["extratags"]);assertFalse(p.containsKey("countrycodes"));assertFalse(p.containsKey("bounded"))
    }
    @Test fun nearbySearchNeedsALocationAndSplitsDateLineBounds() {
        try{PlaceSearch.requests("cafe",nearby=true);fail("location not required")}catch(_:IllegalArgumentException){}
        val boxes=PlaceSearch.requests("cafe",NavPoint(-16.5,179.99),true)
        assertEquals(2,boxes.size)
        boxes.forEach {p->val b=p.getValue("viewbox").split(',').map {it.toDouble()};assertTrue(b[0]<=b[2]);assertEquals("1",p["bounded"])}
        assertEquals(1,PlaceSearch.requests("cafe",NavPoint(90.0,0.0),true).size)
    }
    @Test fun sameBuildingStoresAreDistinctAndNearbyResultsExcludeFarAwayBranches() {
        val center=NavPoint(37.5,127.0)
        val a=PlaceSearch.place("카페 A",37.5,127.0,"1")!!;val b=a.copy(name="카페 B",id="2");val far=a.copy(id="3",point=NavPoint(40.7,-74.0))
        val rows=PlaceSearch.results(listOf(a,b,a,far),center,true)
        assertEquals(listOf("1","2"),rows.map {it.id})
    }
    @Test fun placeDetailsRejectBadCoordinatesAndUnsafeWebsiteSchemes() {
        assertNull(PlaceSearch.place("shop",Double.NaN,127.0));assertNull(PlaceSearch.place("",0.0,0.0))
        val p=PlaceSearch.place("Cafe\nname",48.85,2.35,address="Paris",website="javascript:alert(1)",hours="Mo-Fr 09:00-18:00")!!
        assertEquals("Cafename",p.name);assertEquals("",p.website);assertTrue(p.openingHours.contains("09:00"))
    }
    @Test fun moreResultsExcludeProviderIdsWithoutMixingKakaoIdsIntoNominatimIds() {
        val p=PlaceSearch.requests("shop",excluded=listOf("123","kakao:456","bad,id")).single()
        assertEquals("123,kakao:456",p["exclude_ids"]);assertEquals("123",p["exclude_place_ids"])
    }
}
