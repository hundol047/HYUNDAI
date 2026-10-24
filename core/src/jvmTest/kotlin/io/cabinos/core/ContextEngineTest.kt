package io.cabinos.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContextEngineTest {
    private var now = 1_000_000L
    private lateinit var engine: UnifiedVehicleContextEngine
    private lateinit var hazards: DemoRoadHazardRepository
    @Before fun setup() { hazards = DemoRoadHazardRepository().apply { seed(now) }; engine = UnifiedVehicleContextEngine({ now }, hazards) }
    private fun send(gear: Gear = Gear.PARK, speed: Double = 0.0, charging: Boolean = false, connected: Boolean = true, reliable: Boolean = true, remaining: Long? = null) {
        engine.acceptVehicle(VehicleSample(speed, gear, reliable, connected, charging, 68, remaining, now))
    }
    private fun park(charging: Boolean = false) { send(charging = charging, remaining = if (charging) 2400 else null); now += 1600; send(charging = charging, remaining = if (charging) 2400 else null) }
    @Test fun drivingActivatesOnlyEchoRoad() { send(Gear.DRIVE,80.0); assertTrue(engine.state.value.echoRoadActive); assertFalse(engine.state.value.parkedAllowed) }
    @Test fun trafficStopNeverUnlocksParkedFeatures() { send(Gear.DRIVE,0.0); assertEquals(VehicleContext.STOPPED_IN_TRAFFIC,engine.state.value.context); assertFalse(engine.startSession(SessionKind.FOCUS,25)) }
    @Test fun parkingRequiresDebouncedVerifiedPark() { send(); assertFalse(engine.state.value.parkedAllowed); now+=1600;send();assertTrue(engine.state.value.stopSenseActive);assertTrue(engine.state.value.cabinActive) }
    @Test fun noReliableParkingMeansUnknown() { send(reliable=false);now+=3000;send(reliable=false);assertEquals(VehicleContext.UNKNOWN,engine.state.value.context) }
    @Test fun movingOverridesConflictingParkAndCharge() { park(true);send(speed=1.0,charging=true);assertEquals(VehicleContext.DRIVING,engine.state.value.context);assertFalse(engine.state.value.parkedAllowed) }
    @Test fun staleTelemetryFailsClosed() { park();now+=5001;engine.tick();assertEquals(VehicleContext.UNKNOWN,engine.state.value.context) }
    @Test fun lossOfConnectionPausesRunningSession() { park();engine.setWaitingMinutes(40);engine.startSession(SessionKind.FOCUS,25);send(connected=false);assertEquals(SessionStatus.PAUSED,engine.state.value.session!!.status);assertFalse(engine.state.value.cabinActive) }
    @Test fun chargingActivatesBothParkedSystems() { park(true);assertEquals(VehicleContext.CHARGING,engine.state.value.context);assertTrue(engine.state.value.cabinActive && engine.state.value.stopSenseActive) }
    @Test fun chargingDoesNotSlideDeadlineOnTelemetryTicks() { park(true);val departure=engine.state.value.schedule.departureAtMs;now+=1000;send(charging=true,remaining=2400);assertEquals(departure,engine.state.value.schedule.departureAtMs) }
    @Test fun realChargeEstimateLossClearsTelemetryDeadlineButPreservesManualPlan() {
        fun real(remaining: Long?) = engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,charging=true,chargeRemainingSeconds=remaining,timestampMs=now,source=DataSource.VEHICLE))
        real(2400);now+=1600;real(2400)
        assertNotNull(engine.state.value.schedule.departureAtMs)
        real(null);assertNull(engine.state.value.schedule.departureAtMs)
        assertEquals("충전 시간 확인 필요",engine.state.value.schedule.estimateLabel)
        engine.setWaitingMinutes(30);val manual=engine.state.value.schedule.departureAtMs
        real(600);assertTrue(engine.state.value.schedule.departureAtMs!! < manual!!)
        real(null);assertEquals(manual,engine.state.value.schedule.departureAtMs)
        assertEquals("사용자 설정",engine.state.value.schedule.estimateLabel)
    }
    @Test fun vehicleModeNeverRecommendsSimulationPlaces() {
        fun real() = engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now,source=DataSource.VEHICLE))
        real();now+=1600;real();engine.setWaitingMinutes(40)
        assertTrue(engine.state.value.parkedAllowed)
        assertTrue(engine.state.value.activities.isEmpty())
    }
    @Test fun sharedTimingCapsSessionDuration() { park();engine.setWaitingMinutes(20);engine.startSession(SessionKind.FOCUS,25);assertEquals(900L,engine.state.value.session!!.durationSeconds) }
    @Test fun changingChargeEstimateShortensActiveSession() { park(true);engine.startSession(SessionKind.FOCUS,25);send(charging=true,remaining=600);assertEquals(300L,engine.state.value.session!!.remainingSeconds) }
    @Test fun relaxationKeepsBothServicesActive() { park();engine.setWaitingMinutes(40);engine.startSession(SessionKind.RELAX,30);assertEquals(VehicleContext.RESTING,engine.state.value.context);assertTrue(engine.state.value.stopSenseActive && engine.state.value.cabinActive) }
    @Test fun resumingDriveSuspendsSessionAndRejectsResume() { park();engine.setWaitingMinutes(40);engine.startSession(SessionKind.FOCUS,25);send(Gear.DRIVE,30.0);assertEquals(SessionStatus.PAUSED,engine.state.value.session!!.status);assertFalse(engine.pauseOrResume());assertTrue(engine.state.value.echoRoadActive) }
    @Test fun assistantCannotOverrideDrivingRestrictions() { send(Gear.DRIVE,40.0);engine.assistant("25분 집중 모드 켜줘");engine.assistant("15분 휴식 모드 시작해 줘");assertNull(engine.state.value.session);assertFalse(engine.state.value.parkedAllowed) }
    @Test fun assistantDepartureDoesNotFabricateVehicleMotion() { park();engine.assistant("이제 출발할게");assertEquals(VehicleContext.PARKED,engine.state.value.context) }
    @Test fun departureAlertsAreSingleAndSynchronized() { park();engine.setWaitingMinutes(5);assertEquals(VehicleContext.PREPARING_DEPARTURE,engine.state.value.context);val count=engine.state.value.events.count{it.name=="DepartureApproaching"};engine.tick();assertEquals(1,count);assertEquals(count,engine.state.value.events.count{it.name=="DepartureApproaching"}) }
    @Test fun selectedPlaceReservesTheRoundTripInSharedAvailableTime() { park();engine.setWaitingMinutes(40);engine.selectActivity("cafe");assertEquals(1740L,engine.state.value.schedule.availableSeconds) }
    @Test fun pausedTimerDoesNotTickAndCanResume() { park();engine.setWaitingMinutes(40);engine.startSession(SessionKind.FOCUS,25);engine.pauseOrResume();val value=engine.state.value.session!!.remainingSeconds;now+=2000;send();assertEquals(value,engine.state.value.session!!.remainingSeconds);engine.pauseOrResume();assertEquals(SessionStatus.RUNNING,engine.state.value.session!!.status) }
    @Test fun sessionCompletionRecordedOnce() { park();engine.setWaitingMinutes(20);engine.startSession(SessionKind.FOCUS,1);repeat(61){ now+=1000;send() };assertEquals(SessionStatus.COMPLETED,engine.state.value.session!!.status);assertEquals(1,engine.state.value.history.size);engine.tick();assertEquals(1,engine.state.value.history.size) }
    @Test fun restoredSessionNeverRestoresPhysicalSafety() { val s=CabinSession("s",SessionKind.FOCUS,now,1500,1200);engine.restore(Preferences(),s,listOf(),now+2400000);assertEquals(SessionStatus.PAUSED,engine.state.value.session!!.status);assertEquals(VehicleContext.UNKNOWN,engine.state.value.context) }
    @Test fun locationDenialRemovesHazards() { send(Gear.DRIVE,50.0);assertTrue(engine.state.value.hazards.isNotEmpty());engine.setConnectivity(location=false);assertTrue(engine.state.value.hazards.isEmpty()) }
    @Test fun offlineDoesNotDisableLocalTimers() { park();engine.setWaitingMinutes(40);engine.setConnectivity(network=false);assertTrue(engine.startSession(SessionKind.FOCUS,25));assertFalse(engine.state.value.networkAvailable) }
    @Test fun routeChangeClearsUnrelatedEvents() { send(Gear.DRIVE,40.0);engine.updateRoute(Route("other"));assertTrue(engine.state.value.hazards.isEmpty()) }
    @Test fun negativeOrNonFiniteSpeedFailsClosed() { send(speed=Double.NaN);assertEquals(VehicleContext.UNKNOWN,engine.state.value.context);send(speed=-1.0);assertEquals(VehicleContext.UNKNOWN,engine.state.value.context) }
    @Test fun futureTimestampFailsClosed() { engine.acceptVehicle(VehicleSample(timestampMs=now+10000));assertEquals(VehicleContext.UNKNOWN,engine.state.value.context) }
    @Test fun clockJumpRequiresParkingConfirmationAgain() { park();now-=5000;send();assertFalse(engine.state.value.parkedAllowed) }
    @Test fun optInAndPreferencesCanOnlyChangeWhenParked() { send(Gear.DRIVE,40.0);engine.updatePreferences(Preferences(shareHazards=true));assertFalse(engine.state.value.preferences.shareHazards);park();engine.updatePreferences(Preferences(shareHazards=true));assertTrue(engine.state.value.preferences.shareHazards) }
    @Test fun staleRecoveryRequiresNewParkingDebounce() { park();now+=5001;engine.tick();send();assertFalse(engine.state.value.parkedAllowed);now+=1600;send();assertTrue(engine.state.value.parkedAllowed) }
    @Test fun repeatedCommandsDoNotAccelerateTimer() { park();engine.setWaitingMinutes(40);engine.startSession(SessionKind.FOCUS,25);repeat(20){now+=10;send()};assertEquals(1499L,engine.state.value.session!!.remainingSeconds) }
    @Test fun staleTelemetryCannotUnlockViaUntickedUserAction() { park();engine.setWaitingMinutes(40);now+=5001;assertFalse(engine.startSession(SessionKind.FOCUS,25));assertEquals(VehicleContext.UNKNOWN,engine.state.value.context) }
    @Test fun sampleGapRequiresDebounceEvenWithoutIntermediateTick() { park();now+=6000;send();assertFalse(engine.state.value.parkedAllowed);now+=1600;send();assertTrue(engine.state.value.parkedAllowed) }
    @Test fun sourceChangeRequiresFreshParkingConfirmation() { park();engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now,source=DataSource.VEHICLE));assertFalse(engine.state.value.parkedAllowed) }
    @Test fun outOfOrderSamplesFailClosed() { park();engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now-100));assertEquals(VehicleContext.UNKNOWN,engine.state.value.context) }
    @Test fun invalidatedTelemetryPausesSessionUntilFreshParkingConfirmation() { park();engine.setWaitingMinutes(40);engine.startSession(SessionKind.FOCUS,25);engine.invalidateTelemetry();assertEquals(SessionStatus.PAUSED,engine.state.value.session!!.status);assertFalse(engine.state.value.parkedAllowed);send();assertFalse(engine.state.value.parkedAllowed) }
    @Test fun dataDeletionClearsPersonalStateAndDeadline() { park();engine.setWaitingMinutes(40);engine.startSession(SessionKind.FOCUS,25);assertTrue(engine.clearPersonalData());assertNull(engine.state.value.session);assertTrue(engine.state.value.history.isEmpty());assertNull(engine.state.value.schedule.departureAtMs);assertEquals(Preferences(),engine.state.value.preferences) }
    @Test fun dataDeletionCannotBypassMotionRestriction() { send(Gear.DRIVE,50.0);assertFalse(engine.clearPersonalData());assertTrue(engine.state.value.echoRoadActive) }
    @Test fun completeIntegratedScenario() {
        send(Gear.DRIVE,80.0);assertTrue(engine.state.value.events.any{it.name=="RoadHazardDetected"})
        send(Gear.DRIVE,0.0);assertFalse(engine.state.value.parkedAllowed)
        park(true);assertTrue(engine.state.value.stopSenseActive && engine.state.value.cabinActive)
        assertTrue(engine.startSession(SessionKind.FOCUS,25));send(charging=true,remaining=900)
        assertEquals(600L,engine.state.value.session!!.remainingSeconds)
        send(charging=true,remaining=300);assertEquals(VehicleContext.PREPARING_DEPARTURE,engine.state.value.context)
        send(Gear.DRIVE,60.0);assertTrue(engine.state.value.echoRoadActive);assertFalse(engine.state.value.cabinActive)
    }
    @Test fun drivingClearsPreviousParkingDeadlines() {
        fun real(soc:Int,gear:Gear=Gear.PARK,speed:Double=0.0) = engine.acceptVehicle(VehicleSample(gear=gear,speedKph=speed,parkingReliable=true,batteryPercent=soc,pleosSoc=true,timestampMs=now,source=DataSource.VEHICLE))
        real(70);now+=1600;real(70);engine.setChargeTarget(80);engine.setWaitingMinutes(30)
        real(80);assertEquals(0L,engine.state.value.schedule.availableSeconds)
        real(80,Gear.DRIVE,30.0);assertNull(engine.state.value.schedule.departureAtMs)
        real(60);now+=1600;real(60);engine.setWaitingMinutes(30)
        assertTrue(engine.state.value.schedule.availableSeconds>0)
        assertNotEquals(VehicleContext.PREPARING_DEPARTURE,engine.state.value.context)
        assertEquals(80,engine.state.value.chargeTargetPercent)
    }
    @Test fun restoreRejectsPastDeparture() {
        engine.restore(Preferences(),null,emptyList(),now-1)
        assertNull(engine.state.value.schedule.departureAtMs)
    }
    @Test fun trafficStopAndUnknownPreserveParkingPlan() {
        park();engine.setWaitingMinutes(30);val deadline=engine.state.value.schedule.departureAtMs
        send(Gear.DRIVE,0.0);assertEquals(deadline,engine.state.value.schedule.departureAtMs)
        send(connected=false);assertEquals(deadline,engine.state.value.schedule.departureAtMs)
    }
    @Test fun explicitWaitingPlanReplacesExpiredSocAndRouteDeadlines() {
        fun real() = engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,batteryPercent=80,pleosSoc=true,timestampMs=now,source=DataSource.VEHICLE))
        real();now+=1600;real();engine.setChargeTarget(80);engine.setArrivalInMinutes(10)
        engine.updateNavigationEstimate(900,1000,now)
        repeat(301) { now+=1000;real() }
        assertTrue(engine.setWaitingMinutes(30));assertTrue(engine.state.value.schedule.availableSeconds>0)
    }
    @Test fun drivingDiscardsPastArrivalTarget() {
        park();engine.setArrivalInMinutes(1);now+=61000;send(Gear.DRIVE,20.0)
        assertNull(engine.state.value.arrivalTargetMs)
    }
    @Test fun chargingCountdownDoesNotRepeatDepartureAnnouncements() {
        send(charging=true,remaining=300)
        repeat(10) { now+=1000;send(charging=true,remaining=299L-it) }
        assertEquals(1,engine.state.value.events.count { it.name=="ChargingUpdated" })
        assertEquals(1,engine.state.value.events.count { it.name=="DepartureApproaching" })
    }
    @Test fun materialChargeEstimateJumpAnnouncesOnceMore() {
        send(charging=true,remaining=1200);now+=1000;send(charging=true,remaining=1199)
        send(charging=true,remaining=2100)
        assertEquals(2,engine.state.value.events.count { it.name=="ChargingUpdated" })
    }
}
