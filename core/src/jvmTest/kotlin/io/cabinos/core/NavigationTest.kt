package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class NavigationTest {
    @Test fun preciseGpsWithoutCourseStillHoldsTurnsBetweenVeryCloseHairpinLegs() {
        val p=listOf(NavPoint(0.0,0.0),NavPoint(.001,0.0),NavPoint(.001,.00005),NavPoint(0.0,.00005))
        val r=NavigationGeometry.route(NavPlace("end",p.last()),p,228.0,90.0,listOf(NavManeuver(p.first(),"depart"),NavManeuver(p[1],"turn","right"),NavManeuver(p.last(),"arrive")))
        val t=running(r)
        now+=20_000;t.update(NavFix(NavPoint(.0006,.000025),5.0,now,null,null))
        assertTrue(t.state.tracking);assertTrue(t.state.directionUncertain)
        assertEquals("GPS 위치·진행 방향 확인 중",t.state.nextInstruction)
        assertFalse(t.state.offRoute);assertFalse(t.state.arrived)
        now+=1000;t.update(NavFix(NavPoint(.0007,0.0),5.0,now,10.0,0.0))
        assertFalse(t.state.directionUncertain)
    }

    @Test fun opposingRoadsInsideGpsUncertaintyHoldTurnsAndRecoverWithPreciseCourse() {
        val p=listOf(NavPoint(0.0,0.0),NavPoint(.002,0.0),NavPoint(.002,.0003),NavPoint(0.0,.0003))
        val r=NavigationGeometry.route(NavPlace("end",p.last()),p,478.0,90.0,listOf(NavManeuver(p.first(),"depart"),NavManeuver(p[1],"turn","right"),NavManeuver(p.last(),"arrive")))
        val t=running(r)
        now+=30_000;t.update(NavFix(p[2],5.0,now,5.0,180.0))
        now+=5000;t.update(NavFix(NavPoint(.0015,.00015),25.0,now,null,null))
        assertTrue(t.state.tracking);assertTrue(t.state.directionUncertain);assertFalse(t.state.offRoute)
        assertEquals("GPS 위치·진행 방향 확인 중",t.state.nextInstruction);assertFalse(t.state.arrived)
        now+=1000;t.update(NavFix(NavPoint(.0014,.0003),5.0,now,5.0,180.0))
        assertFalse(t.state.directionUncertain);assertTrue(t.state.tracking);assertFalse(t.state.offRoute)
        assertNotEquals("GPS 위치·진행 방향 확인 중",t.state.nextInstruction)
    }
    @Test fun continuityNeverPrefersADistantSegmentOverAnAvailablePlausibleRoad() {
        val p=listOf(NavPoint(0.0,0.0),NavPoint(.002,0.0),NavPoint(.002,.002),NavPoint(0.0,.002))
        val r=NavigationGeometry.route(NavPlace("end",p.last()),p,667.0,90.0,listOf(NavManeuver(p.first(),"depart"),NavManeuver(p.last(),"arrive")))
        val (_,gap)=NavigationGeometry.project(r,NavPoint(.001,0.0),550.0,null,1000.0,40.0,2.0,550.0,50.0)
        assertTrue("A close route segment must take priority over continuity outside GPS tolerance",gap<1)
        val (_,offGap)=NavigationGeometry.project(r,NavPoint(.001,.005),550.0,null,1000.0,40.0,2.0,550.0,50.0)
        assertTrue("A truly off-road fix must remain off road",offGap>50)
    }
    @Test fun recordedNarrowIstanbulLoopKeepsProgressWithCoarseGpsAndNoCourse() {
        val points=javaClass.getResourceAsStream("/world-istanbul.csv")!!.bufferedReader().useLines {lines->
            lines.filter {it.isNotBlank()&&!it.startsWith("#")}.map {line->val v=line.split(',');NavPoint(v[0].toDouble(),v[1].toDouble())}.toList()
        }
        val total=points.zipWithNext().sumOf {(a,b)->NavigationGeometry.distance(a,b)}
        val r=NavigationGeometry.route(NavPlace("Recorded road",points.last()),points,total,600.0,listOf(NavManeuver(points.first(),"depart"),NavManeuver(points.last(),"arrive")))
        for(accuracy in listOf(25.0,50.0)) for(speed in listOf(10.0,null)) {
            val tracker=running(r);var segment=0;var at=10.0;var index=0
            while(at<total-100) {
                while(segment<points.lastIndex-1&&r.cumulative[segment+1]<at)segment++
                val t=(at-r.cumulative[segment])/(r.cumulative[segment+1]-r.cumulative[segment])
                val lat=points[segment].latitude+(points[segment+1].latitude-points[segment].latitude)*t
                val lon=points[segment].longitude+(points[segment+1].longitude-points[segment].longitude)*t
                val angle=index*2.399963229728653;val radius=accuracy-1
                val noisy=NavPoint(lat+kotlin.math.cos(angle)*radius/111195,lon+kotlin.math.sin(angle)*radius/(111195*kotlin.math.cos(lat*kotlin.math.PI/180)))
                now+=1000;tracker.update(NavFix(noisy,accuracy,now,speed))
                assertTrue(tracker.state.tracking);assertFalse("False off-route at $at m / accuracy $accuracy",tracker.state.offRoute)
                assertFalse(tracker.state.arrived);assertTrue(kotlin.math.abs(tracker.state.progressMeters-at)<150)
                at+=10;index++
            }
        }
    }
    @Test fun denselySampledSegmentsKeepCourseDirectionAndDuplicateVerticesAreNotCandidates() {
        val p=(0..50).map {NavPoint(it*.000018,0.0)}+
            (50 downTo 0).flatMap {listOf(NavPoint(it*.000018,.0001),NavPoint(it*.000018,.0001))}
        val r=NavigationGeometry.route(NavPlace("end",p.last()),p,211.0,90.0,listOf(NavManeuver(p.first(),"depart"),NavManeuver(p.last(),"arrive")))
        val (at,gap)=NavigationGeometry.project(r,NavPoint(.00045,0.0),100.0,180.0,400.0)
        assertTrue("Short/duplicate segments must not attract the fix to the opposing leg",at>r.cumulative[51])
        assertTrue(gap<12)
    }
    @Test fun courseCanDisambiguateTheReturnLegWithinCoarsePositionUncertainty() {
        val p=listOf(NavPoint(0.0,0.0),NavPoint(.001,0.0),NavPoint(.001,.00045),NavPoint(0.0,.00045))
        val r=NavigationGeometry.route(NavPlace("end",p.last()),p,272.0,90.0,listOf(NavManeuver(p.first(),"depart"),NavManeuver(p.last(),"arrive")))
        val (at,gap)=NavigationGeometry.project(r,NavPoint(.0005,0.0),111.0,180.0,400.0,100.0)
        assertTrue(at>r.cumulative[2]);assertTrue(gap<51)
    }
    @Test fun noisyFixPrefersTheCurrentSameHeadingLegAfterANarrowLoop() {
        val p=listOf(NavPoint(0.0,0.0),NavPoint(.001,0.0),NavPoint(.001,.0001),NavPoint(0.0,.0001),NavPoint(0.0,.0002),NavPoint(.001,.0002))
        val r=NavigationGeometry.route(NavPlace("end",p.last()),p,355.0,90.0,listOf(NavManeuver(p.first(),"depart"),NavManeuver(p.last(),"arrive")))
        val (progress,gap)=NavigationGeometry.project(r,NavPoint(.0005,.00005),300.0,0.0,400.0)
        assertTrue("Must not jump back to the first parallel leg",progress>r.cumulative[4])
        assertTrue(gap<25)
    }
    @Test fun rejoiningFurtherAheadRecoversEvenWhenReroutingWasUnavailable() {
        val t=running();now+=10_000;t.update(fix(NavPoint(37.001,127.0),speed=10.0))
        now+=3000;t.update(fix(NavPoint(37.001,127.002),speed=10.0));assertTrue(t.state.offRoute)
        now+=40_000;t.update(fix(NavPoint(37.007,127.002),speed=10.0));assertTrue(t.state.offRoute)
        now+=3000;t.update(fix(NavPoint(37.007,127.0),speed=10.0))
        assertTrue(t.state.tracking);assertFalse(t.state.offRoute);assertFalse(t.state.rerouteRequired)
        assertTrue(t.state.progressMeters>750)
    }
    @Test fun reportedGpsUncertaintyDoesNotBecomeFalseReverseTravel() {
        val t=running()
        for(i in 1..50){
            now+=1000
            val at=(i*5.0+if(i%2==1)49.0 else -49.0).coerceAtLeast(0.0)
            t.update(fix(NavPoint(37.0+at/111195.0,127.0),accuracy=50.0,speed=5.0))
            assertTrue(t.state.tracking);assertFalse("False reverse travel at noisy fix $i",t.state.offRoute)
            assertFalse(t.takeReroute())
        }
        // A real sustained reversal larger than the uncertainty still reroutes.
        for(at in listOf(120.0,80.0,40.0)){
            now+=3000;t.update(fix(NavPoint(37.0+at/111195.0,127.0),accuracy=50.0,speed=5.0))
        }
        assertTrue(t.state.offRoute);assertTrue(t.takeReroute())
    }
    @Test fun finalLegNeverAnnouncesCompletedArrivalBeforeTwoArrivalFixesInEveryLanguage() {
        assertEquals(NavigationLanguages.codes.toSet(),navDestinationLabels.keys)
        val points=listOf(NavPoint(46.0,8.0),NavPoint(46.02,8.0))
        val base=NavigationGeometry.route(NavPlace("Destination",points.last()),points,2224.0,500.0,
            listOf(NavManeuver(points.first(),"depart"),NavManeuver(points.last(),"arrive",road="Furkastrasse")))
        for(language in NavigationLanguages.codes+listOf("de-CH","th-TH","hi-IN")) {
            val r=base.localized(language);val tracker=running(r)
            now+=30_000;tracker.update(fix(NavPoint(46.01,8.0),speed=5.0))
            assertTrue(tracker.state.active);assertFalse(tracker.state.arrived)
            assertTrue(tracker.state.turnMeters>1000)
            assertEquals(NavigationLanguages.upcomingArrival(language,"Furkastrasse"),tracker.state.nextInstruction)
            assertNotEquals(NavigationLanguages.arrival(language,0.0),tracker.state.nextInstruction)
            now+=30_000;tracker.update(fix(points.last(),speed=0.0))
            assertFalse(tracker.state.arrived)
            now+=1000;tracker.update(fix(points.last(),speed=0.0))
            assertTrue(tracker.state.arrived);assertFalse(tracker.state.active)
            assertEquals(NavigationLanguages.arrival(language,0.0),tracker.state.nextInstruction)
        }
        assertEquals("Ziel · Furkastrasse",NavigationLanguages.upcomingArrival("de-CH","Furkastrasse"))
        assertEquals("Destination",NavigationLanguages.upcomingArrival("en-US"))
        assertEquals("목적지",NavigationLanguages.upcomingArrival("ko-KR"))
    }
    @Test fun unnamedMotorwayRampsKeepDestinationSignsWithoutInventingRoadNames() {
        assertEquals("19: 남원주, 충주",NavigationGeometry.roadLabel("","19: 남원주, 충주"))
        assertEquals("신평RampA1 · 55: 안동",NavigationGeometry.roadLabel("신평RampA1","55: 안동"))
        assertEquals("A1",NavigationGeometry.roadLabel("","","A1"))
        assertEquals("기존 도로",NavigationGeometry.roadLabel("기존 도로","기존 도로"))
    }
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
    @Test fun sparseTurnAnchorsKeepTheRightOccurrenceAfterALoop() {
        val a=NavPoint(48.85,2.35);val b=NavPoint(48.86,2.35);val c=NavPoint(48.86,2.36);val end=NavPoint(48.87,2.36)
        val p=listOf(a,b,a,c,b,end)
        val r=NavigationGeometry.route(NavPlace("loop",end),p,7000.0,900.0,listOf(NavManeuver(a,"depart"),NavManeuver(c,"turn","right"),NavManeuver(b,"turn","left"),NavManeuver(end,"arrive")))
        assertEquals(listOf(0.0,r.cumulative[3],r.cumulative[4],r.cumulative[5]),r.steps.map {it.atMeters})
    }
    @Test fun slightlyOffsetTurnAnchorsStillUseTheNearestForwardRoadPoint() {
        val p=listOf(NavPoint(48.85,2.35),NavPoint(48.86,2.35),NavPoint(48.87,2.36))
        val r=NavigationGeometry.route(NavPlace("offset",p.last()),p,3000.0,600.0,listOf(NavManeuver(p.first(),"depart"),NavManeuver(NavPoint(48.860001,2.35),"turn","right"),NavManeuver(p.last(),"arrive")))
        assertEquals(r.cumulative[1],r.steps[1].atMeters,0.0)
    }
    @Test fun boundedProjectionFindsTheMiddleOfASparseLongSegment() {
        val points=listOf(NavPoint(0.0,0.0),NavPoint(0.0,1.0))
        val r=NavigationGeometry.route(NavPlace("long segment",points.last()),points,111195.0,6000.0,listOf(NavManeuver(points[0],"depart"),NavManeuver(points[1],"arrive")))
        val (at,gap)=NavigationGeometry.project(r,NavPoint(0.0,.455),50_000.0,90.0,1000.0)
        assertEquals(50_593.725,at,.1);assertEquals(0.0,gap,.01)
    }
    @Test fun denseGlobalRouteProjectionMatchesUnboundedGeometryNearCurrentProgress() {
        for((lat,lon) in listOf(-33.9 to 18.4,-23.5 to -46.6,52.5 to 13.4)) {
            val points=(0..100_000).map {NavPoint(lat,lon+it*.000002)}
            val r=NavigationGeometry.route(NavPlace("dense road",points.last()),points,20000.0,1500.0,listOf(NavManeuver(points.first(),"depart"),NavManeuver(points.last(),"arrive")))
            for(i in listOf(0,1,50_000,99_999,100_000)) {
                val p=points[i];val previous=r.cumulative[i]
                val full=NavigationGeometry.project(r,p,previous,90.0)
                val bounded=NavigationGeometry.project(r,p,previous,90.0,300.0)
                assertEquals(full.first,bounded.first,.001);assertEquals(full.second,bounded.second,.001)
            }
        }
    }
    @Test fun boundedProjectionHandlesDuplicatePointsAndDateLineBoundary() {
        val a=NavPoint(0.0,179.99);val b=NavPoint(0.0,-179.99)
        val points=listOf(a,a,b,b)
        val r=NavigationGeometry.route(NavPlace("date line",b),points,2224.0,200.0,listOf(NavManeuver(a,"depart"),NavManeuver(b,"arrive")))
        val (at,gap)=NavigationGeometry.project(r,NavPoint(0.0,180.0),1111.95,90.0,50.0)
        assertEquals(r.geometryMeters/2,at,1.0);assertEquals(0.0,gap,.01)
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
    @Test fun coarseFixesNearDestinationWaitForTwoPreciseArrivalFixes() {
        val r=route();val t=running(r)
        repeat(3){now+=40_000;t.update(fix(r.points.last(),accuracy=50.0,speed=0.0))}
        assertFalse(t.state.arrived);assertTrue(t.state.active)
        assertTrue(t.state.status.contains("정확한 GPS"))
        now+=1000;t.update(fix(r.points.last(),accuracy=5.0,speed=0.0));assertFalse(t.state.arrived)
        now+=1000;t.update(fix(r.points.last(),accuracy=5.0,speed=0.0));assertTrue(t.state.arrived)
    }
    @Test fun invalidGpsAndSignalLossBreakTheConsecutiveArrivalEvidence() {
        for(interruption in listOf("poor","stale","background")){
            val r=route();val t=running(r)
            now+=40_000;t.update(fix(r.points.last(),speed=0.0));assertFalse(t.state.arrived)
            when(interruption){
                "poor"->{now+=1000;t.update(fix(r.points.last(),accuracy=100.0,speed=0.0))}
                "stale"->{now+=11_000;t.tick()}
                else->t.suspendTracking()
            }
            now+=1000;t.update(fix(r.points.last(),speed=0.0));assertFalse(interruption,t.state.arrived)
            now+=1000;t.update(fix(r.points.last(),speed=0.0));assertTrue(interruption,t.state.arrived)
        }
    }
    @Test fun duplicateFixDoesNotAdvanceOrCountArrivalTwice() {
        val r=route();val t=running(r);now+=40_000;val f=fix(r.points.last());t.update(f);t.update(f)
        assertFalse(t.state.arrived)
    }
    @Test fun backgroundRequiresNewFixAndDoesNotContinueVoice() {
        val t=running();t.suspendTracking();assertFalse(t.state.tracking);assertNull(t.state.fix);assertTrue(t.state.active)
        now+=8000;t.update(fix(NavPoint(37.005,127.0)));assertTrue(t.state.tracking)
    }
    @Test fun roundaboutIconDoesNotAssumeAClockwiseTrafficDirection() {
        for (type in listOf("roundabout", "rotary")) {
            val maneuver=NavManeuver(NavPoint(0.0,0.0),type,exit=3)
            assertEquals("◎",NavigationGeometry.symbol(maneuver))
            assertTrue(NavigationGeometry.instruction(maneuver).contains("3번째"))
        }
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
        now+=2000;t.update(fix(NavPoint(37.0103,127.0003),speed=5.0))
        assertEquals("목적지",t.state.nextInstruction);assertFalse(t.state.arrived)
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
