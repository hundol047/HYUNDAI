package io.cabinos.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/** One source of truth for vehicle safety, both parked services, and every departure/session timer.
 * All mutations must run on the same dispatcher (Android Main in the app).
 */
class UnifiedVehicleContextEngine(
    private val clock: () -> Long = ::platformNowMillis,
    private val hazards: RoadHazardRepository = DemoRoadHazardRepository(),
    private val recommendations: ActivityRecommendationRepository = DemoActivityRepository(),
    private val decision: ContextDecisionEngine = SafetyDecisionEngine(),
) : VehicleStateRepository {
    private val mutable = MutableStateFlow(AppState(nowMs = clock()))
    override val state = mutable.asStateFlow()
    private val ranker = HazardRanker()
    private val alertGate = HazardAlertGate()
    private var parkCandidateAt: Long? = null
    private var sessionDeadlineMs: Long? = null
    private var departureAtMs: Long? = null
    private var manualDepartureAtMs: Long? = null
    private var lastChargeEstimate: Long? = null
    private var chargeDeadlineMs: Long? = null
    private var socDeadlineMs: Long? = null
    private var routeDeadlineMs: Long? = null
    private var routeReadAtMs: Long? = null
    private var returnSeconds = 0L
    private var nearbyActivities: List<NearbyActivity>? = null
    private var scheduleLabel = "시간 미설정"
    private var lastTickMs = clock()
    private var eventId = 0L
    private var departureAnnounced = false
    private var driving = false

    private var navigationLock=false
    fun setNavigationLock(locked:Boolean) {
        if(navigationLock==locked)return
        navigationLock=locked;parkCandidateAt=null;tick()
    }

    fun acceptVehicle(sample: VehicleSample) {
        val now = clock()
        val previousSample = state.value.sample
        if (previousSample != null && (sample.timestampMs - previousSample.timestampMs > 5_000 || sample.source != previousSample.source)) parkCandidateAt = null
        if (previousSample != null && sample.source == previousSample.source && sample.timestampMs < previousSample.timestampMs) {
            parkCandidateAt = null
            mutable.value = state.value.copy(sample = sample.copy(connected = false))
            tick(); return
        }
        if (sample.parkingReliable && sample.gear == Gear.PARK && sample.speedKph == 0.0 && sample.connected && now - sample.timestampMs in 0..5_000) {
            if (parkCandidateAt == null) parkCandidateAt = now
        } else parkCandidateAt = null
        if (sample.charging && sample.chargeRemainingSeconds != null && sample.chargeRemainingSeconds != lastChargeEstimate) {
            // The provider reports a remaining duration at this timestamp; do not slide the deadline on each tick.
            val chargeDeadline = now + sample.chargeRemainingSeconds.coerceAtLeast(0) * 1000
            chargeDeadlineMs = chargeDeadline
            scheduleLabel = if (sample.source == DataSource.SIMULATION) "시뮬레이션 충전 예상" else "차량 충전 예상"
            lastChargeEstimate = sample.chargeRemainingSeconds
            departureAnnounced = false
            event("ChargingUpdated", "충전 예상 시간이 변경되었습니다.")
        }
        if (sample.source == DataSource.VEHICLE && lastChargeEstimate != null &&
            (!sample.charging || sample.chargeRemainingSeconds == null)) {
            // An expired/unsupported charge estimate must not keep masquerading as live telemetry.
            chargeDeadlineMs = null
            scheduleLabel = if(manualDepartureAtMs != null) "사용자 설정" else "충전 시간 확인 필요"
            lastChargeEstimate = null
        }
        if (!sample.charging) { lastChargeEstimate = null; chargeDeadlineMs = null }
        val target = state.value.chargeTargetPercent
        if (target != null && sample.source == DataSource.VEHICLE && sample.connected && sample.pleosSoc &&
            now - sample.timestampMs in 0..3_000 && sample.batteryPercent in target..100 && socDeadlineMs == null) {
            // A measured SOC threshold ends the activity now and reserves preparation time.
            // Never extrapolate a charging curve or write a target to the vehicle.
            socDeadlineMs = now + state.value.preferences.departureBufferMinutes * 60_000L
            event("ChargeTargetReached", "Pleos SOC가 계획 목표에 도달했습니다. 출발을 준비하세요.")
        }
        mutable.value = mutable.value.copy(sample = sample)
        tick()
    }

    fun tick() {
        val now = clock()
        val old = mutable.value
        if (now < lastTickMs || now - lastTickMs > 10_000) {
            parkCandidateAt = null // Wake/resume and clock jumps require fresh parking confirmation.
        }
        lastTickMs = now
        if (old.sample == null || !old.sample.connected || now - old.sample.timestampMs !in 0..5_000) parkCandidateAt = null
        val vehicleContext = decision.decide(old.sample, now, parkCandidateAt?.let { now - it >= 1500 } == true)
        if (vehicleContext == VehicleContext.DRIVING && !driving) {
            socDeadlineMs=null; manualDepartureAtMs=null; chargeDeadlineMs=null; lastChargeEstimate=null
            routeDeadlineMs=null; routeReadAtMs=null; departureAnnounced=false; scheduleLabel="시간 미설정"
            if (old.arrivalTargetMs?.let { it < now } == true) mutable.value=state.value.copy(arrivalTargetMs=null)
        }
        driving = vehicleContext == VehicleContext.DRIVING
        val routeFresh = routeReadAtMs?.let { now - it in 0..10_000 } == true
        val constraints = listOfNotNull(
            manualDepartureAtMs?.let { it to "사용자 설정" },
            chargeDeadlineMs?.let { it to if(old.sample?.source == DataSource.SIMULATION) "시뮬레이션 충전 예상" else "차량 충전 예상" },
            socDeadlineMs?.let { it to "Pleos SOC 목표 도달" },
            routeDeadlineMs?.let { it to if(routeFresh) "NaviHelper 도착 계획" else "내비 정보 만료 · 이전 마감 유지" },
        )
        constraints.minByOrNull { it.first }?.let { departureAtMs=it.first; scheduleLabel=it.second }
            ?: run { departureAtMs=null }
        val schedule = TimeOptimizer.calculate(departureAtMs, now, old.preferences.departureBufferMinutes * 60L, returnSeconds, scheduleLabel)
        if (old.sample == null || !old.sample.connected || now - old.sample.timestampMs !in 0..5_000) parkCandidateAt = null
        val physical = if(navigationLock)VehicleContext.DRIVING else vehicleContext
        val allowed = physical == VehicleContext.PARKED || physical == VehicleContext.CHARGING
        var session = old.session
        if (!allowed && session?.status == SessionStatus.RUNNING) {
            session = session.copy(status = SessionStatus.PAUSED,
                remainingSeconds = sessionDeadlineMs?.let { ((it - now) / 1000).coerceAtLeast(0) } ?: session.remainingSeconds)
            sessionDeadlineMs = null
            event("SessionSafelySuspended", "차량 상태가 바뀌어 세션을 안전하게 일시정지했습니다.")
        }
        if (allowed && session?.status == SessionStatus.RUNNING) {
            val scheduleDeadline = departureAtMs?.minus((schedule.bufferSeconds + schedule.returnSeconds) * 1000) ?: now
            sessionDeadlineMs = min(sessionDeadlineMs ?: now, scheduleDeadline)
            val remaining = ((sessionDeadlineMs!! - now) / 1000).coerceAtLeast(0)
            session = session.copy(remainingSeconds = remaining)
            if (remaining == 0L) {
                session = session.copy(status = SessionStatus.COMPLETED)
                event("SessionCompleted", "${session.kind.label} 세션이 끝났습니다. 출발 시간을 확인하세요.")
            }
        }
        val context = when {
            !allowed -> physical
            schedule.departureAtMs != null && schedule.waitingSeconds <= schedule.bufferSeconds -> VehicleContext.PREPARING_DEPARTURE
            session?.status == SessionStatus.RUNNING && session.kind == SessionKind.RELAX -> VehicleContext.RESTING
            else -> physical
        }
        val ranked = if (!navigationLock && old.sample?.source == DataSource.SIMULATION && old.locationAvailable) ranker.rank(hazards.events(now), old.route, now) else emptyList()
        val history = if (session?.status == SessionStatus.COMPLETED && old.history.none { it.id == session.id })
            (listOf(session) + old.history).take(100) else old.history
        mutable.value = mutable.value.copy(
            context = context, parkedAllowed = allowed,
            echoRoadActive = physical == VehicleContext.DRIVING || physical == VehicleContext.STOPPED_IN_TRAFFIC,
            stopSenseActive = allowed, cabinActive = allowed, schedule = schedule, session = session, history = history,
            hazards = ranked, nowMs = now,
            activities = if (allowed) TimeOptimizer.recommend(
                (nearbyActivities ?: recommendations.activities()).filter { old.sample?.source != DataSource.VEHICLE || !it.simulated },
                schedule, old.preferences.preferredCategory) else emptyList(),
        )
        if (context != old.context) event("ContextChanged", "${contextLabel(old.context)} → ${contextLabel(context)}")
        if (allowed && context == VehicleContext.PREPARING_DEPARTURE && !departureAnnounced) {
            departureAnnounced = true; event("DepartureApproaching", "출발 준비 시간입니다. 진행 중인 활동을 정리해 주세요.")
        }
        if (mutable.value.echoRoadActive && alertGate.take(ranked.firstOrNull(), now)) {
            val h = ranked.first(); event("RoadHazardDetected", "전방 ${kotlin.math.round(h.distanceMeters / 100) / 10}킬로미터 구간에 ${h.hazard.kind} 정보가 있습니다. 시뮬레이션 안내입니다.")
        }
    }

    fun setWaitingMinutes(minutes: Int): Boolean {
        if (!parkedNow()) return reject()
        val now=clock()
        if (socDeadlineMs?.let { it < now } == true) socDeadlineMs=null
        if (routeDeadlineMs?.let { it < now } == true) { routeDeadlineMs=null;routeReadAtMs=null }
        manualDepartureAtMs = now + minutes.coerceIn(1, 240) * 60_000L
        departureAtMs = manualDepartureAtMs
        scheduleLabel = "사용자 설정"; departureAnnounced = false
        event("DepartureTimeChanged", "출발까지 ${minutes.coerceIn(1,240)}분으로 설정했습니다."); tick(); return true
    }
    fun setChargeTarget(percent: Int?): Boolean {
        if (!parkedNow() || (percent != null && percent !in 50..100)) return false
        mutable.value = state.value.copy(chargeTargetPercent=percent)
        socDeadlineMs=null
        state.value.sample?.let { acceptVehicle(it) } ?: tick()
        return true
    }
    fun setArrivalInMinutes(minutes: Int?): Boolean {
        if (!parkedNow()) return reject()
        mutable.value=state.value.copy(arrivalTargetMs=minutes?.coerceIn(1,1440)?.let { clock()+it*60_000L })
        routeDeadlineMs=null; routeReadAtMs=null; tick(); return true
    }
    /** Seconds/meters from DestinationInfo, not route geometry or an assumed driving speed.
     * A missing/stale route retains the earlier conservative deadline until the user replans. */
    fun updateNavigationEstimate(seconds: Int?, meters: Int?, readAtMs: Long?) {
        val arrival=state.value.arrivalTargetMs ?: return
        if(seconds == null || meters == null || readAtMs == null || seconds !in 0..604800 || meters < 0 ||
            clock()-readAtMs !in 0..10_000) { routeReadAtMs=null; tick(); return }
        routeReadAtMs=readAtMs
        val updated=arrival-seconds*1000L
        routeDeadlineMs=routeDeadlineMs?.let { min(it,updated) } ?: updated
        tick()
    }
    fun selectActivity(id: String): Boolean {
        if (!parkedNow()) return reject()
        val option = state.value.activities.firstOrNull { it.activity.id == id && it.feasible } ?: return false
        returnSeconds = 2 * option.activity.oneWaySeconds
        mutable.value = state.value.copy(selectedActivityId=id)
        event("ActivitySelected", "${option.activity.name} 선택 · 왕복 ${returnSeconds / 60}분을 확보했습니다."); tick(); return true
    }
    fun updateNearbyActivities(origin:MapPosition,activities:List<NearbyActivity>):Boolean {
        if(!parkedNow() || !origin.valid())return false
        nearbyActivities=activities.filter { it.latitude!=null && it.longitude!=null && MapPosition(it.latitude,it.longitude).valid() && it.name.isNotBlank() && it.oneWaySeconds>=60 && it.minimumActivitySeconds>=0 }.distinctBy { it.id }.take(60)
        val selected=nearbyActivities!!.firstOrNull { it.id==state.value.selectedActivityId }
        returnSeconds=selected?.oneWaySeconds?.times(2) ?: 0
        mutable.value=state.value.copy(activityOrigin=origin,selectedActivityId=selected?.id,nearbyRevision=state.value.nearbyRevision+1)
        tick();return true
    }
    fun stayInCabin() { if (parkedNow()) { returnSeconds = 0;mutable.value=state.value.copy(selectedActivityId=null);tick() } }
    fun startSession(kind: SessionKind, minutes: Int): Boolean {
        if (!parkedNow()) return reject()
        val duration = min(minutes.coerceIn(1,120) * 60L, state.value.schedule.availableSeconds)
        if (duration < 60) { event("SessionRejected", "출발까지 여유 시간이 부족합니다. 대기 시간을 먼저 설정해 주세요."); return false }
        archiveActive()
        val session = CabinSession(randomEventId(), kind, clock(), duration, duration)
        mutable.value = mutable.value.copy(session = session)
        sessionDeadlineMs = clock() + duration * 1000
        event("${kind.name}SessionStarted", "${duration / 60}분 ${kind.label} 세션을 시작합니다."); tick(); return true
    }
    fun pauseOrResume(): Boolean {
        if (!parkedNow()) return reject()
        val s = state.value.session ?: return false
        if (s.status == SessionStatus.RUNNING) {
            mutable.value = state.value.copy(session = s.copy(status = SessionStatus.PAUSED)); sessionDeadlineMs = null
        } else if (s.status == SessionStatus.PAUSED && state.value.schedule.availableSeconds > 0) {
            val remaining = min(s.remainingSeconds, state.value.schedule.availableSeconds)
            mutable.value = state.value.copy(session = s.copy(status = SessionStatus.RUNNING, remainingSeconds = remaining))
            sessionDeadlineMs = clock() + remaining * 1000
        }
        tick(); return true
    }
    private fun archiveActive() {
        state.value.session?.let { s ->
            if (state.value.history.none { it.id == s.id }) {
                mutable.value = state.value.copy(history = (listOf(s.copy(status = SessionStatus.INTERRUPTED)) + state.value.history).take(100))
            }
        }
    }
    fun endSession() { if (parkedNow()) { archiveActive(); mutable.value = state.value.copy(session = null); sessionDeadlineMs = null; tick() } }
    fun updatePreferences(preferences: Preferences) {
        if (!parkedNow()) { reject(); return }
        mutable.value = state.value.copy(preferences = preferences.copy(focusMinutes = preferences.focusMinutes.coerceIn(1,90),
            relaxMinutes = preferences.relaxMinutes.coerceIn(1,90), departureBufferMinutes = preferences.departureBufferMinutes.coerceIn(1,15)))
        tick()
    }
    fun restore(preferences: Preferences, session: CabinSession?, history: List<CabinSession>, departure: Long?) {
        // Never restore a physical safety state or automatically resume a session after process death.
        mutable.value = state.value.copy(preferences = preferences, session = session?.let {
            if (it.status == SessionStatus.RUNNING) it.copy(status = SessionStatus.PAUSED) else it
        }, history = history.take(100))
        val restored=departure?.takeIf { it >= clock() }
        departureAtMs = restored; manualDepartureAtMs = restored
        scheduleLabel = if(restored!=null) "저장된 출발 계획" else "시간 미설정"; tick()
    }
    fun setConnectivity(network: Boolean = state.value.networkAvailable, location: Boolean = state.value.locationAvailable) {
        mutable.value = state.value.copy(networkAvailable = network, locationAvailable = location)
        event("ConnectivityChanged", if (!network) "오프라인 모드 · 로컬 기능은 계속 사용할 수 있습니다." else if (!location) "위치 사용 불가 · 경로 위험 안내를 중단합니다." else "연결 상태가 복구되었습니다."); tick()
    }
    fun updateRoute(route: Route) { mutable.value = state.value.copy(route = route); tick() }
    fun injectHazard() {
        if (state.value.sample?.source != DataSource.SIMULATION) return
        val now = clock()
        hazards.add(RoadHazard(randomEventId(), "도로 장애물", state.value.route.id,
            state.value.route.progressMeters + 800, state.value.route.heading, 1.0, .96, 3, now, now + 600_000))
        event("SimulatedHazardInjected", "시뮬레이션 위험 이벤트를 추가했습니다."); tick()
    }
    fun assistant(command: String): String {
        tick()
        val c = command.replace(" ", "")
        val minutes = Regex("(\\d+)분").find(c)?.groupValues?.get(1)?.toIntOrNull()
        val response = when {
            c.contains("위험") -> if (!state.value.locationAvailable) "위치 정보가 없어 경로 위험을 확인할 수 없습니다." else state.value.hazards.firstOrNull()?.let { "전방 ${it.distanceMeters.toInt()}미터에 ${it.hazard.kind} 정보가 있습니다. 데모 데이터입니다." } ?: "현재 경로에 확인된 주요 위험 알림이 없습니다."
            c.contains("집중") -> if (startSession(SessionKind.FOCUS, minutes ?: state.value.preferences.focusMinutes)) "집중 세션을 시작했습니다." else state.value.message
            c.contains("휴식") -> if (startSession(SessionKind.RELAX, minutes ?: state.value.preferences.relaxMinutes)) "휴식 세션을 시작했습니다." else state.value.message
            c.contains("충전") -> if (state.value.sample?.charging == true) {
                state.value.sample?.chargeRemainingSeconds?.let { "충전 잔여 시간은 ${(it+59)/60}분입니다. ${if(state.value.sample?.source==DataSource.SIMULATION) "시뮬레이션" else "차량 제공 예상"}입니다." }
                    ?: "충전 상태는 확인했지만 차량이 남은 시간을 제공하지 않았습니다. 출발 계획을 직접 설정해 주세요."
            } else "현재 확인된 충전 정보가 없습니다."
            c.contains("전에알려") -> if (state.value.parkedAllowed) { updatePreferences(state.value.preferences.copy(departureBufferMinutes = minutes ?: 5)); "출발 ${state.value.preferences.departureBufferMinutes}분 전에 알려드릴게요." } else "주행 중에는 출발 설정을 변경할 수 없습니다."
            c.contains("출발") -> if (state.value.parkedAllowed) { endSession(); "세션을 정리했습니다. 차량 신호가 주행을 확인하면 자동 전환합니다." } else "EchoRoad가 주행 상태를 확인하고 있습니다."
            c.contains("추천") || c.contains("쉴곳") -> if (state.value.parkedAllowed) state.value.activities.firstOrNull { it.feasible }?.let { "${it.activity.name}, 편도 약 ${it.activity.oneWaySeconds / 60}분입니다. ${if(it.activity.simulated) "예시 장소입니다." else "OpenStreetMap 등록 장소이며 도보 추정입니다."}" } ?: "지금은 차량 안에서 짧게 쉬는 것을 추천합니다." else "주행 중에는 주변 활동 추천을 사용할 수 없습니다."
            c.contains("주행") -> "현재 ${contextLabel(state.value.context)} 상태입니다."
            else -> "위험 구간, 충전 시간, 집중·휴식 세션을 물어보세요."
        }
        event("AssistantResponse", response); return response
    }
    fun invalidateTelemetry(reason: String = "차량 연결을 다시 확인합니다.") {
        parkCandidateAt = null
        mutable.value = state.value.copy(sample = state.value.sample?.copy(connected = false))
        tick()
        event("TelemetryInvalidated", reason)
    }
    fun clearPersonalData(): Boolean {
        if (!parkedNow()) return reject()
        departureAtMs = null; manualDepartureAtMs = null; lastChargeEstimate = null
        chargeDeadlineMs=null; socDeadlineMs=null; routeDeadlineMs=null; routeReadAtMs=null
        sessionDeadlineMs = null; returnSeconds = 0; departureAnnounced = false; scheduleLabel = "시간 미설정"
        nearbyActivities=null
        mutable.value = state.value.copy(preferences = Preferences(), chargeTargetPercent=null, arrivalTargetMs=null, session = null, history = emptyList(), events = emptyList(),activityOrigin=MapPosition(),selectedActivityId=null,nearbyRevision=state.value.nearbyRevision+1)
        tick()
        event("PersonalDataCleared", "할 일·세션 기록·개인 설정을 삭제했습니다.")
        return true
    }
    private fun parkedNow(): Boolean { tick(); return state.value.parkedAllowed }
    private fun reject(): Boolean { event("SafetyRestricted", "안전한 주차가 확인되어야 사용할 수 있습니다."); return false }
    private fun event(name: String, message: String) {
        val e = ContextEvent(++eventId, name, message, clock())
        mutable.value = mutable.value.copy(events = (mutable.value.events + e).takeLast(40), message = message)
    }
    companion object {
        fun contextLabel(c: VehicleContext) = when(c) {
            VehicleContext.UNKNOWN -> "상태 확인 중"; VehicleContext.DRIVING -> "주행 중"; VehicleContext.STOPPED_IN_TRAFFIC -> "신호 대기 중"
            VehicleContext.PARKED -> "주차 중"; VehicleContext.CHARGING -> "충전 중"; VehicleContext.RESTING -> "휴식 중"; VehicleContext.PREPARING_DEPARTURE -> "출발 준비"
        }
    }
}
