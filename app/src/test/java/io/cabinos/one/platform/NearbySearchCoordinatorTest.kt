package io.cabinos.one.platform

import io.cabinos.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NearbySearchCoordinatorTest {
    private fun place(category:String)=DemoActivityRepository().activities().first().copy(id=category,category=category,simulated=false)
    @Test fun partialPrimaryResultsStillSupplementMissingCategories()=runBlocking {
        var requested=setOf<String>()
        val result=NearbySearchCoordinator.search({i->if(i==0)throw java.io.IOException() else listOf(place(NearbySearchCoordinator.categories[i]))},{missing->requested=missing;listOf(place("카페"))})
        assertEquals(setOf("카페"),requested);assertEquals(4,result.places.size);assertTrue(result.complete)
    }
    @Test fun emptyCategoryAlsoUsesFallbackAndKeepsOtherResultsOnFailure()=runBlocking {
        val result=NearbySearchCoordinator.search({i->if(i==0)emptyList() else listOf(place(NearbySearchCoordinator.categories[i]))},{throw java.io.IOException()})
        assertEquals(3,result.places.size);assertFalse(result.complete);assertTrue(result.notice.contains("카페"))
    }
    @Test fun allCategoriesPopulatedAvoidsExtraPublicServerRequests()=runBlocking {
        val result=NearbySearchCoordinator.search({i->listOf(place(NearbySearchCoordinator.categories[i]))},{error("unnecessary fallback")})
        assertEquals(4,result.places.size);assertTrue(result.complete)
    }
    @Test fun totalFailureIsNotAnEmptySuccess()=runBlocking {
        try {NearbySearchCoordinator.search({throw java.io.IOException()},{throw java.io.IOException()});fail("must preserve prior results via failure")}
        catch(_:java.io.IOException){}
    }
    @Test fun cancellationDoesNotTryFallback()=runBlocking {
        try {NearbySearchCoordinator.search({throw CancellationException()},{error("cancelled")});fail("must cancel")}catch(_:CancellationException){}
    }
}
