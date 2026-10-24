package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class RegionalPlaceSearchTest {
    @Test fun sameCityStationDoesNotForceRecoveryToAnUnrelatedShorterRegionName() {
        val city=NavPlace("Kuala Lumpur",NavPoint(3.1516964,101.6942371),category="administrative")
        val station=NavPlace("Kuala Lumpur",NavPoint(3.1407803,101.6939423),category="station")
        assertEquals(city,RegionalPlaceSearch.chooseRegion(listOf(station,city),NavPoint(3.15,101.69)))
        assertEquals(1200,RegionalPlaceSearch.radius("mall"));assertEquals(20000,RegionalPlaceSearch.radius("city"))
        val request=PlaceSearch.requests("shop",NavPoint(0.0,179.999),true,radius=1200.0)
        assertEquals(2,request.size);assertTrue(request.all {it["radius"]=="1200"})
    }
    @Test fun categoryAndNameChecksRejectTheWrongBusinessEvenWhenTheProviderReturnsIt() {
        assertFalse(RegionalPlaceSearch.acceptsCategory("Padaria Bella Paulista","newsagent"))
        assertFalse(RegionalPlaceSearch.acceptsCategory("Hotel Belvedere","station"))
        assertTrue(RegionalPlaceSearch.acceptsCategory("Padaria Bella Paulista","베이커리"))
        assertTrue(RegionalPlaceSearch.matchesName("Padaria Bella Paulista","Bella Paulista Casa dos Pães"))
        assertFalse(RegionalPlaceSearch.matchesName("Padaria Bella Paulista","La Bella"))
        assertTrue(RegionalPlaceSearch.matchesName("스타벅스 대구","Starbucks","반월당, 대구광역시"))
        assertTrue(RegionalPlaceSearch.matchesName("The Remarkables Sweet Shop","The Remarkable Sweet Shop"))
        assertFalse(RegionalPlaceSearch.aliasKey("name:pronunciation"))
        assertTrue(RegionalPlaceSearch.aliasKey("name:zh-Hant"))
    }
    @Test fun splitsRetainEveryWordAndTryMultiwordRegionsBeforeSingleWordRegions() {
        val query="Starbucks Dubai Mall"
        val splits=RegionalPlaceSearch.splits(query)
        assertEquals(listOf("Dubai Mall","Mall"),splits.map {it.region})
        assertTrue(splits.all {it.name+" "+it.region==query})
        assertTrue(RegionalPlaceSearch.splits("Starbucks").isEmpty())
        assertTrue(RegionalPlaceSearch.splits("a".repeat(181)).isEmpty())
    }
    @Test fun aShopIsNeverAcceptedAsARegionAndAliasesMustMatchExactly() {
        assertTrue(RegionalPlaceSearch.regionMatches("Sao Paulo",listOf("São Paulo"),"city"))
        assertTrue(RegionalPlaceSearch.regionMatches("Toronto",listOf("토론토","Toronto"),"administrative"))
        assertFalse(RegionalPlaceSearch.regionMatches("Tokyo",listOf("Tokyo Cafe"),"city"))
        assertFalse(RegionalPlaceSearch.regionMatches("Toronto",listOf("Toronto"),"cafe"))
    }
    @Test fun ambiguousRegionsNeedNearbyEvidenceAndNeverChooseDistantNamesakes() {
        val a=NavPlace("Paris",NavPoint(48.85,2.35));val b=NavPlace("Paris",NavPoint(33.66,-95.55))
        assertNull(RegionalPlaceSearch.chooseRegion(listOf(a,b),null))
        assertNull(RegionalPlaceSearch.chooseRegion(listOf(a,b),NavPoint(37.5,127.0)))
        assertEquals(a,RegionalPlaceSearch.chooseRegion(listOf(a,b),a.point))
        assertEquals(a,RegionalPlaceSearch.chooseRegion(listOf(a,a.copy(point=NavPoint(48.8501,2.3501))),null))
    }
    @Test fun nameVariantsAreBoundedAndKeepTheOriginalFirst() {
        assertEquals(listOf("Balzacs","Balzac"),RegionalPlaceSearch.names("Balzacs"))
        assertTrue("OldTown White Coffee" in RegionalPlaceSearch.names("Old Town White Coffee"))
        assertTrue("Bella Paulista" in RegionalPlaceSearch.names("Padaria Bella Paulista"))
        assertTrue("Fidel" in RegionalPlaceSearch.names("Fidel Cafe"))
        assertTrue("Remarkable Sweet" in RegionalPlaceSearch.names("The Remarkables Sweet Shop"))
        assertTrue(RegionalPlaceSearch.names("cafe").all {it.isNotBlank()})
    }
    @Test fun candidatesExplicitlyDiscloseBroaderScopeAndVoiceRequiresAnExactCommand() {
        val p=NavPlace("店",NavPoint(35.0,139.0));val result=RegionalSearchResult(listOf(p),"Tokyo","店")
        assertTrue(result.notice.contains("20km"));assertTrue(result.notice.contains("정확히 일치"))
        assertEquals(NavigationVoiceAction.REGIONAL,NavigationVoice.parse("지역 나눠 찾기").action)
        assertEquals(NavigationVoiceAction.SEARCH,NavigationVoice.parse("지역 나눠 찾기 카페 서울").action)
    }
}
