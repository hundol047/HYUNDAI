package io.cabinos.core

import kotlinx.coroutines.flow.StateFlow

enum class VehicleContext { UNKNOWN, DRIVING, STOPPED_IN_TRAFFIC, PARKED, CHARGING, RESTING, PREPARING_DEPARTURE }
enum class DataSource { SIMULATION, VEHICLE }
enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }
enum class SessionKind(val label: String) { FOCUS("집중"), RELAX("휴식"), AUDIO("오디오") }
enum class SessionStatus { RUNNING, PAUSED, COMPLETED, INTERRUPTED }
data class VehicleSample(
    val speedKph: Double = 0.0, val gear: Gear = Gear.UNKNOWN,
    val parkingReliable: Boolean = false, val connected: Boolean = true,
    val charging: Boolean = false, val batteryPercent: Int = 68,
    val chargeRemainingSeconds: Long? = null, val timestampMs: Long,
    val source: DataSource = DataSource.SIMULATION,
)
data class Preferences(
    val focusMinutes: Int = 25, val relaxMinutes: Int = 30,
    val departureBufferMinutes: Int = 5, val voiceEnabled: Boolean = true,
    val shareHazards: Boolean = false, val preferredCategory: String = "전체",
)
data class CabinSession(
    val id: String, val kind: SessionKind, val startedAtMs: Long,
    val durationSeconds: Long, val remainingSeconds: Long,
    val status: SessionStatus = SessionStatus.RUNNING,
)
data class SharedSchedule(
    val departureAtMs: Long? = null, val waitingSeconds: Long = 0,
    val bufferSeconds: Long = 300, val returnSeconds: Long = 0,
    val availableSeconds: Long = 0, val estimateLabel: String = "시간 미설정",
)
data class Route(val id: String = "demo-pangyo", val progressMeters: Double = 0.0, val heading: Double = 0.0)
data class RoadHazard(
    val id: String, val kind: String, val routeId: String, val routeMeters: Double,
    val heading: Double, val severity: Double, val confidence: Double,
    val reports: Int, val reportedAtMs: Long, val expiresAtMs: Long,
    val simulated: Boolean = true,
)
data class RankedHazard(val hazard: RoadHazard, val distanceMeters: Double, val score: Double)
data class NearbyActivity(
    val id: String, val name: String, val category: String, val oneWaySeconds: Long,
    val minimumActivitySeconds: Long, val description: String,
    val simulated: Boolean = true,
)
data class ActivityOption(val activity: NearbyActivity, val usableSeconds: Long, val feasible: Boolean)
data class ContextEvent(val id: Long, val name: String, val message: String, val timestampMs: Long)
data class AppState(
    val context: VehicleContext = VehicleContext.UNKNOWN,
    val parkedAllowed: Boolean = false, val echoRoadActive: Boolean = false,
    val stopSenseActive: Boolean = false, val cabinActive: Boolean = false,
    val sample: VehicleSample? = null, val schedule: SharedSchedule = SharedSchedule(),
    val preferences: Preferences = Preferences(), val session: CabinSession? = null,
    val history: List<CabinSession> = emptyList(), val hazards: List<RankedHazard> = emptyList(),
    val activities: List<ActivityOption> = emptyList(), val events: List<ContextEvent> = emptyList(),
    val message: String = "차량 상태를 확인하고 있습니다.",
    val networkAvailable: Boolean = true, val locationAvailable: Boolean = true,
    val route: Route = Route(), val nowMs: Long = 0,
)
interface VehicleDataProvider { val samples: StateFlow<VehicleSample> }
interface VehicleStateRepository { val state: StateFlow<AppState> }
interface NavigationProvider { val route: StateFlow<Route>; fun resumeNavigation(): Boolean }
interface LocationProvider { val available: StateFlow<Boolean>; fun start(); fun stop() }
interface SpeechProvider { fun speak(text: String); fun listen(onResult: (String) -> Unit); fun close() }
interface ChargingProvider { val remainingSeconds: StateFlow<Long?> }
interface RoadHazardRepository { fun events(nowMs: Long): List<RoadHazard>; fun add(event: RoadHazard) }
interface ActivityRecommendationRepository { fun activities(): List<NearbyActivity> }
interface CabinSessionRepository { suspend fun save(session: CabinSession); suspend fun history(): List<CabinSession> }
interface ContextDecisionEngine { fun decide(sample: VehicleSample?, nowMs: Long, safeParkingConfirmed: Boolean): VehicleContext }
