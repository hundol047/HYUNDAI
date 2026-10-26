package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class WalkingPlanTest {
    @Test fun recommendationUsesAsymmetricRoundTripAndRequiresFootRouteForRealPlaces() {
        val origin=MapPosition();val place=NearbyActivity("p","장소","카페",60,600,"",false,37.395,127.111)
        val schedule=TimeOptimizer.calculate(1800000,0,300)
        assertFalse(TimeOptimizer.recommend(listOf(place),schedule,"전체").single().feasible)
        val walk=WalkingPlan(origin,120,480,600.0,listOf(origin,origin),0)
        val option=TimeOptimizer.recommend(listOf(place.copy(walking=walk)),schedule,"전체").single()
        assertTrue(option.feasible);assertEquals(900L,option.usableSeconds)
        assertFalse(walk.usable(900001,origin))
        assertFalse(walk.usable(0,MapPosition(37.4,127.2)))
    }
    @Test fun engineRefusesUnverifiedOrStaleWalkAndReservesBothLegs() {
        var now=1000000L;val e=UnifiedVehicleContextEngine({now},DemoRoadHazardRepository())
        fun sample(){e.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))}
        sample();now+=1600;sample();e.setWaitingMinutes(30)
        val origin=MapPosition();val place=NearbyActivity("p","장소","카페",60,600,"",false,37.395,127.111)
        e.updateNearbyActivities(origin,listOf(place));assertFalse(e.selectActivity("p"))
        val plan=WalkingPlan(origin,120,480,600.0,listOf(origin,origin),now)
        assertFalse(e.confirmWalking("p",origin,plan.copy(checkedAtMs=now-900001)))
        assertTrue(e.confirmWalking("p",origin,plan));assertTrue(e.selectActivity("p"))
        assertEquals(600L,e.state.value.schedule.returnSeconds)
        assertEquals(480L,e.state.value.schedule.inboundSeconds)
        assertEquals(900L,e.state.value.schedule.availableSeconds)
        e.updateNearbyActivities(origin,listOf(place))
        assertEquals("p",e.state.value.selectedActivityId)
        assertEquals(600L,e.state.value.schedule.returnSeconds)
        assertEquals(480L,e.state.value.schedule.inboundSeconds)
    }
}
