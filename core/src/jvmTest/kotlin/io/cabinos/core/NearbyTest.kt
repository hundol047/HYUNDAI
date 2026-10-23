package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class NearbyTest {
    @Test fun otherRegisteredLanguagesArePreservedWithoutTreatingMetadataAsNames() {
        assertEquals("喫茶店",NearbyGeometry.placeName(mapOf("name:fr" to "\u202e", "name:ja" to "喫茶店", "name:pronunciation" to "not a name")))
        assertEquals("茶館",NearbyGeometry.placeName(mapOf("name:zh-Hant" to "茶館")))
        assertEquals("",NearbyGeometry.placeName(mapOf("name:pronunciation" to "phonetic", "name:etymology" to "metadata")))
        assertEquals("한국 이름",NearbyGeometry.placeName(mapOf("name:ko" to "한국 이름", "name" to "Local name", "name:fr" to "Nom")))
        assertTrue(NearbyGeometry.query(MapPosition()).contains("name:[A-Za-z]{2,3}"))
    }

    @Test fun nearbyNamesUseRegisteredEnglishFallbackAndQueriesFilterNamesBeforeLimiting() {
        assertEquals("English only",NearbyGeometry.placeName("\u202e"," ","English only"))
        assertEquals("현지 이름",NearbyGeometry.placeName("","현지 이름","English name"))
        val query=NearbyGeometry.query(MapPosition(48.85,2.35))
        assertEquals(3,query.split("[~").size-1)
        assertTrue(query.endsWith("out center tags 60;"))
        try{NearbyGeometry.query(MapPosition(Double.NaN,0.0));fail("Invalid origin must not reach the provider")}catch(_:IllegalArgumentException){}
    }
    private var now=1_000_000L
    private fun parked():UnifiedVehicleContextEngine {
        val engine=UnifiedVehicleContextEngine({now})
        engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        now+=1600;engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now));engine.setWaitingMinutes(40)
        return engine
    }
    @Test fun distanceAndWalkingEstimateAreBoundedAndRoundedUp() {
        val a=MapPosition(37.0,127.0);val b=MapPosition(37.001,127.0)
        assertEquals(0.0,NearbyGeometry.distanceMeters(a,a),0.001)
        assertEquals(111.2,NearbyGeometry.distanceMeters(a,b),1.0)
        assertEquals(180L,NearbyGeometry.walkingSeconds(a,b))
        assertEquals(60L,NearbyGeometry.walkingSeconds(a,a))
    }
    @Test fun invalidRemoteCoordinatesAndDistantPlacesAreRejected() {
        val origin=MapPosition()
        assertNull(NearbyGeometry.osmActivity("x","장소","카페",Double.NaN,127.0,origin))
        assertNull(NearbyGeometry.osmActivity("x","장소","카페",0.0,0.0,origin))
        assertNull(NearbyGeometry.osmActivity("x","","카페",origin.latitude,origin.longitude,origin))
        assertNull(NearbyGeometry.osmActivity("x","장소","임의",origin.latitude,origin.longitude,origin))
    }
    @Test fun actualPlacesCarryCoordinatesAndEstimateDisclosure() {
        val p=NearbyGeometry.osmActivity("osm-node-1","실제 등록명","카페",37.395,127.111,MapPosition())!!
        assertFalse(p.simulated);assertEquals(37.395,p.latitude!!,0.000001);assertTrue(p.description.contains("추정"))
    }
    @Test fun placeSelectionReservesReturnTimeAndNewOriginClearsOldPlan() {
        val e=parked();val initial=e.state.value.schedule.availableSeconds
        assertTrue(e.selectActivity("cafe"));assertEquals("cafe",e.state.value.selectedActivityId)
        assertEquals(initial-360,e.state.value.schedule.availableSeconds)
        val origin=MapPosition(37.5,127.1,"직접 선택한 기준점")
        assertTrue(e.updateNearbyActivities(origin,emptyList()))
        assertNull(e.state.value.selectedActivityId);assertEquals(0L,e.state.value.schedule.returnSeconds)
        assertTrue(e.state.value.activities.isEmpty());assertEquals(origin,e.state.value.activityOrigin)
    }
    @Test fun movingVehicleCannotChangeOriginOrApplyPlacePlan() {
        val e=parked();e.acceptVehicle(VehicleSample(speedKph=10.0,gear=Gear.DRIVE,timestampMs=now))
        assertFalse(e.updateNearbyActivities(MapPosition(37.5,127.1),emptyList()))
        assertFalse(e.selectActivity("cafe"));assertEquals(MapPosition(),e.state.value.activityOrigin)
    }
    @Test fun deletingPersonalDataAlsoClearsInMemoryLocation() {
        val e=parked();e.updateNearbyActivities(MapPosition(37.5,127.1,"기기 위치"),emptyList())
        assertTrue(e.clearPersonalData());assertEquals(MapPosition(),e.state.value.activityOrigin)
    }
}
