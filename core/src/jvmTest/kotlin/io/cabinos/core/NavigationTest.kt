package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class NavigationTest {
    private var now=100_000L
    private fun route(lat:Double=37.0,lon:Double=127.0):DrivingRoute {
        val points=listOf(NavPoint(lat,lon),NavPoint(lat+.01,lon),NavPoint(lat+.02,lon+.01))
        return NavigationGeometry.route(NavPlace("목적지",points.last()),points,3000.0,600.0,listOf(NavManeuver(points[0],"depart"),NavManeuver(points[1],"turn","right","테스트 도로"),NavManeuver(points[2],"arrive")))
    }
    private fun fix(p:NavPoint,accuracy:Double=5.0,speed:Double=1.0)=NavFix(p,accuracy,now,speed)
    private fun running(r:DrivingRoute=route()):NavigationTracker=NavigationTracker({now}).apply {preview(r);assertTrue(start(fix(r.points.first())))}
    @Test fun worldCoordinatesHaveNoKoreaRestriction() {
        for((lat,lon) in listOf(37.5 to 127.0,48.8 to 2.3,40.7 to -74.0,35.6 to 139.7,-33.8 to 151.2,-33.9 to 18.4)) {
            val r=route(lat,lon);val t=running(r);now+=8000;t.update(fix(NavPoint(lat+.005,lon)))
            assertTrue(t.state.tracking);assertTrue(t.state.remainingMeters<3000);assertTrue(t.state.nextInstruction.contains("우회전"))
        }
    }
    @Test fun longRoutesWithThousandsOfStepsUseOrderedExactAnchors() {
        val points=(0..20_000).map {NavPoint(40.0+it*.00001,-74.0)}
        val maneuvers=(0..1000).map {NavManeuver(points[it*20],if(it==1000)"arrive" else "continue")}
        val r=NavigationGeometry.route(NavPlace("장거리",points.last()),points,23000.0,2000.0,maneuvers)
        assertEquals(1001,r.steps.size);assertTrue(r.steps.zipWithNext().all {(a,b)->a.atMeters<=b.atMeters})
    }
    @Test fun sustainedBackwardTravelOnSameRoadAlsoRequestsRerouting() {
        val t=running();now+=8000;t.update(fix(NavPoint(37.004,127.0),speed=10.0))
        now+=1000;t.update(fix(NavPoint(37.003,127.0),speed=10.0));assertTrue(t.state.offRoute)
        now+=3000;t.update(fix(NavPoint(37.002,127.0),speed=10.0))
        now+=3000;t.update(fix(NavPoint(37.001,127.0),speed=10.0));assertTrue(t.takeReroute())
    }
    @Test fun dateLineUsesShortDistanceAndProjection() {
        val a=NavPoint(0.0,179.99);val b=NavPoint(0.0,-179.99)
        assertTrue(NavigationGeometry.distance(a,b) in 2200.0..2250.0)
        val r=NavigationGeometry.route(NavPlace("동쪽",b),listOf(a,b),2224.0,200.0,listOf(NavManeuver(a,"depart"),NavManeuver(b,"arrive")))
        val (at,gap)=NavigationGeometry.project(r,NavPoint(0.0,180.0))
        assertEquals(r.geometryMeters/2,at,1.0);assertTrue(gap<1)
    }
    @Test fun stalePoorAndFutureFixesCannotStart() {
        val t=NavigationTracker({now});val r=route();t.preview(r)
        assertFalse(t.start(fix(r.points[0],51.0)))
        assertFalse(t.start(fix(r.points[0]).copy(timestampMs=now-10_001)))
        assertFalse(t.start(fix(r.points[0]).copy(timestampMs=now+1)))
        assertFalse(t.start(fix(NavPoint(0.0,0.0))))
        assertFalse(t.start(fix(NavPoint(37.0,127.001))))
        assertTrue(t.start(fix(r.points[0])))
    }
    @Test fun gpsLossPausesGuidanceWithoutInventingProgress() {
        val t=running();val before=t.state.progressMeters;now+=10_001;t.tick()
        assertFalse(t.state.tracking);assertEquals(before,t.state.progressMeters,0.0);assertTrue(t.state.active)
    }
    @Test fun offRouteRequiresSustainedEvidenceAndRateLimitsRequests() {
        val t=running();val p=NavPoint(37.005,127.005)
        now+=10_000;t.update(fix(p));assertFalse(t.state.rerouteRequired)
        now+=3000;t.update(fix(p));assertFalse(t.state.rerouteRequired)
        now+=3000;t.update(fix(p));assertTrue(t.state.rerouteRequired);assertTrue(t.takeReroute());assertFalse(t.takeReroute())
        now+=1000;t.update(fix(p));assertFalse(t.takeReroute())
        now+=30_000;t.update(fix(p));assertTrue(t.takeReroute())
    }
    @Test fun freshRouteReplacesOldProgressUsingCurrentFix() {
        val t=running();now+=8000;t.update(fix(NavPoint(37.005,127.0)))
        assertTrue(t.replaceRoute(route(37.005,127.0)));assertEquals(3000.0,t.state.remainingMeters,2.0);assertFalse(t.state.offRoute)
    }
    @Test fun arrivalNeedsTwoNearbyFixesNotHighSpeedPass() {
        val r=route();val t=running(r);now+=40_000;t.update(fix(r.points.last(),speed=20.0));assertFalse(t.state.arrived)
        now+=40_000;t.update(fix(r.points.last()));assertFalse(t.state.arrived)
        now+=40_000;t.update(fix(r.points.last()));assertTrue(t.state.arrived);assertFalse(t.state.active)
    }
    @Test fun duplicateFixDoesNotAdvanceOrCountArrivalTwice() {
        val r=route();val t=running(r);now+=40_000;val f=fix(r.points.last());t.update(f);t.update(f)
        assertFalse(t.state.arrived)
    }
    @Test fun backgroundRequiresNewFixAndDoesNotContinueVoice() {
        val t=running();t.suspendTracking();assertFalse(t.state.tracking);assertNull(t.state.fix);assertTrue(t.state.active)
        now+=8000;t.update(fix(NavPoint(37.005,127.0)));assertTrue(t.state.tracking)
    }
    @Test fun koreanTurnsIncludeRoundaboutExitAndNativeRoadName() {
        assertTrue(NavigationGeometry.instruction(NavManeuver(NavPoint(0.0,0.0),"roundabout",exit=3)).contains("3번째"))
        assertTrue(NavigationGeometry.instruction(NavManeuver(NavPoint(0.0,0.0),"turn","left","Rue de Rivoli")).contains("좌회전 · Rue de Rivoli"))
    }
    @Test fun malformedRouteAndUnreachableDestinationAreRejected() {
        fun rejected(block:()->Unit){try{block();fail("invalid route accepted")}catch(_:IllegalArgumentException){}}
        rejected {NavigationGeometry.route(NavPlace("원거리",NavPoint(0.0,0.0)),route().points,10.0,5.0,listOf(NavManeuver(route().points[0],"depart")))}
        rejected {NavigationGeometry.route(NavPlace("x",NavPoint(0.0,0.0)),listOf(NavPoint(Double.NaN,0.0),NavPoint(0.0,0.0)),10.0,5.0,listOf(NavManeuver(NavPoint(0.0,0.0),"depart")))}
    }
    @Test fun reachingRoadAccessPointDoesNotClaimArrivalAtDistantBuilding() {
        val old=route();val r=NavigationGeometry.route(NavPlace("건물",NavPoint(37.02,127.015)),old.points,3000.0,600.0,listOf(NavManeuver(old.points.first(),"depart"),NavManeuver(old.points.last(),"arrive")))
        assertTrue(r.destinationAccessMeters>100)
        val t=running(r);now+=40_000;t.update(fix(r.points.last(),speed=0.0));now+=1000;t.update(fix(r.points.last(),speed=0.0))
        assertTrue(t.state.arrived);assertTrue(t.state.nextInstruction.contains("자동차 도착 지점"));assertTrue(t.state.nextInstruction.contains("목적지까지"))
    }
    @Test fun impossibleGpsJumpPausesWithoutTeleportingOrRerouting() {
        val t=running();now+=1000;t.update(fix(NavPoint(38.0,128.0),speed=10.0))
        assertFalse(t.state.tracking);assertFalse(t.state.rerouteRequired);assertEquals(0.0,t.state.progressMeters,0.0)
        now+=1000;t.update(fix(NavPoint(37.0005,127.0),speed=10.0));assertTrue(t.state.tracking)
    }
    @Test fun turnInstructionRemainsUntilIntersectionIsActuallyPassed() {
        val r=route();val t=running(r)
        for(lat in listOf(37.0098,37.00995,37.01,37.01005)) {
            now+=20_000;t.update(fix(NavPoint(lat,127.0),speed=5.0));assertTrue(t.state.nextInstruction.contains("우회전"))
        }
        now+=2000;t.update(fix(NavPoint(37.0103,127.0003),speed=5.0));assertTrue(t.state.nextInstruction.contains("도착"))
    }
    @Test fun headingHelpsDistinguishOppositeParallelRouteSegments() {
        val points=listOf(NavPoint(37.0,127.0),NavPoint(37.01,127.0),NavPoint(37.01,127.0002),NavPoint(37.0,127.0002))
        val r=NavigationGeometry.route(NavPlace("남쪽",points.last()),points,2300.0,300.0,listOf(NavManeuver(points[0],"depart"),NavManeuver(points.last(),"arrive")))
        val north=NavigationGeometry.project(r,NavPoint(37.005,127.0001),heading=0.0).first
        val south=NavigationGeometry.project(r,NavPoint(37.005,127.0001),heading=180.0).first
        assertTrue(north<r.geometryMeters/2);assertTrue(south>r.geometryMeters/2)
    }
    @Test fun navigationLockOverridesSimulatedParkingAndSuppressesDemoHazards() {
        val e=UnifiedVehicleContextEngine({now});fun park(){e.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))}
        park();now+=1600;park();e.setWaitingMinutes(40);e.startSession(SessionKind.FOCUS,25);assertTrue(e.state.value.parkedAllowed)
        e.setNavigationLock(true);assertFalse(e.state.value.parkedAllowed);assertTrue(e.state.value.echoRoadActive);assertEquals(SessionStatus.PAUSED,e.state.value.session?.status)
        now+=2000;park();assertFalse(e.state.value.parkedAllowed);assertTrue(e.state.value.hazards.isEmpty())
        e.setNavigationLock(false);assertFalse(e.state.value.parkedAllowed);park();now+=1600;park();assertTrue(e.state.value.parkedAllowed)
    }
}
