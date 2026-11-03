package io.cabinos.one.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import io.cabinos.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground-only location: no background permission/service and no stored GPS history. */
class LiveNavigation(
    private val context: Context,
    private val engine: UnifiedVehicleContextEngine,
    private val speech: AndroidSpeechProvider,
    val service: NavigationService = NavigationService(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val echoProbe: EchoRoadClient = EchoRoadClient(),
) {
    val voiceAvailable = speech.voiceAvailable
    private val tracker = NavigationTracker(clock)
    private val preferences = RuntimePreferences(context)
    private var language = preferences.navigationLanguage
    private val mutable = MutableStateFlow(NavigationUi(language = language))
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val roadSafety = RoadSafetyRepository(context, scope, clock)
    private val safetyAnnouncements = RoadSafetyAnnouncements()
    private val manager = context.getSystemService(LocationManager::class.java)
    private var job: Job? = null
    private var generation = 0
    private var foreground = true
    private var listening = false
    private var safetyHeld = false
    private val stationary = StationaryEvidence()
    private val repeatGuard = GuidanceRepeatGuard()
    private val trafficClient = TrafficClient()
    private var trafficJob: Job? = null
    private var trafficLastAttempt = 0L
    private var trafficGeneration = 0

    private val echoClient=EchoRoadClient()
    private var echoCheckJob:Job?=null
    private var echoCheckGeneration=0
    private val brakeDetector=BrakeDetector()
    private var echoJob:Job?=null
    private var echoGeneration=0
    private var echoRealFix=false
    private var echoSharing=false
    private var echoAttempt=0L
    private var echoVoiceAt=0L
    private val echoSpoken=mutableSetOf<String>()

    private fun cancelEchoCheck() {
        echoCheckGeneration++;echoCheckJob?.cancel();echoProbe.cancel()
        if(mutable.value.echoChecking)mutable.value=mutable.value.copy(echoChecking=false,echoNotice="연결 확인 중단 · 다시 연결을 눌러 주세요.")
    }
    fun configureEcho(endpoint:String,token:String,sharing:Boolean=false) {
        check(canPlan()) { "안내 종료 후 안전하게 정차한 상태에서 연결하세요." }
        if(mutable.value.echoChecking && endpoint.isNotBlank())return
        // Validate before changing the currently configured client. Never store a key on disk.
        echoProbe.configure(endpoint,token)
        cancelEchoCheck()
        echoGeneration++;echoJob?.cancel();echoClient.cancel();echoClient.configure(endpoint,token)
        echoSharing=sharing && echoClient.endpoint.isNotEmpty()
        engine.updatePreferences(engine.state.value.preferences.copy(shareHazards=echoSharing))
        brakeDetector.reset();echoAttempt=0L;echoSpoken.clear()
        val configured=echoClient.endpoint.isNotEmpty()
        mutable.value=mutable.value.copy(echoRisks=emptyList(),echoAhead=null,echoConnected=false,echoChecking=configured,
            echoNotice=if(!configured)"EchoRoad 공유 서버 미연결" else "서버·접근키 확인 중… · 위치는 전송하지 않습니다.")
        if(!configured)return
        val epoch=echoCheckGeneration
        echoCheckJob=scope.launch {
            try {
                val official=echoProbe.verify()
                if(epoch==echoCheckGeneration && foreground)mutable.value=mutable.value.copy(echoConnected=true,
                    echoNotice="서버 연결·접근키 인증 성공 · "+if(official)"사고 다발 자료 연결" else "사고 다발 자료 미적재 · 주변 제보는 GPS 안내 중 조회")
            } catch(error:Exception) {
                if(error is CancellationException)throw error
                if(epoch==echoCheckGeneration)mutable.value=mutable.value.copy(echoConnected=false,echoNotice=EchoRoadClient.connectionError(error))
            } finally {
                if(epoch==echoCheckGeneration)mutable.value=mutable.value.copy(echoChecking=false)
            }
        }
    }
    private fun echoObservation(f:NavFix,location:Location) {
        if(!tracker.state.active || location.provider!=LocationManager.GPS_PROVIDER || location.isFromMockProvider || !location.hasSpeedAccuracy() || !location.speedAccuracyMetersPerSecond.isFinite() || location.speedAccuracyMetersPerSecond !in 0f..1.5f) {brakeDetector.reset();return}
        val event=brakeDetector.accept(f,clock()) ?: return
        if(!mutable.value.echoConnected || !echoSharing || !engine.state.value.preferences.shareHazards || echoClient.endpoint.isEmpty()) {
            mutable.value=mutable.value.copy(echoNotice="급감속 후보 감지 · 공유하지 않음");return
        }
        if(echoJob?.isActive==true)return // No location queue and no delayed uploads.
        val epoch=echoGeneration
        echoJob=scope.launch {
            try {echoClient.report(event);if(epoch==echoGeneration)mutable.value=mutable.value.copy(echoNotice="급감속 후보 전송 · 다른 기기 확인 전 미게시")}
            catch(e:Exception){if(e is CancellationException)throw e;if(epoch==echoGeneration)mutable.value=mutable.value.copy(echoNotice="위험 제보 전송 실패 · 저장·재전송하지 않음")}
        }
    }
    private fun echoTick() {
        val g=tracker.state;val f=fix
        val ready=mutable.value.echoConnected && foreground && echoRealFix && ((g.active && g.tracking) || canPlan()) && f!=null && tracker.usable(f) && clock()-f.timestampMs in 0..3000 && engine.state.value.networkAvailable
        val fresh=if(ready)mutable.value.echoRisks.filter {it.expiresAtMs>clock()} else emptyList()
        val ahead=if(ready)EchoRoadMatch.ahead(g,fresh,clock()) else null
        mutable.value=mutable.value.copy(echoRisks=fresh,echoAhead=ahead?.let {(if(it.second==0)"현재 경로 주변" else "약 ${it.second}m 앞")+" · ${it.first.label} · "+if(it.first.area!=null)"과거 통계·참고 정보" else "기기 제보·참고 정보"})
        if(!ready){brakeDetector.reset();echoJob?.cancel();echoClient.cancel();return}
        if(ahead!=null && ahead.first.id !in echoSpoken && clock()-echoVoiceAt>=30000 && g.turnMeters>150 && !mutable.value.voiceListening && !speech.navigationSpeaking && engine.state.value.preferences.voiceEnabled) {
            val text=if(NavigationLanguages.resolve(language)=="ko")(if(ahead.second==0)"현재 경로 주변은 " else "약 ${ahead.second}미터 앞 경로 주변은 ")+"${ahead.first.label.replace("~","년부터 ")}입니다. 현장 상황을 확인하세요." else "Road risk observations ahead. Check road conditions."
            if(speech.speakNavigation(text,language)){echoSpoken+=ahead.first.id;echoVoiceAt=clock()}
        }
        if(echoClient.endpoint.isEmpty() || echoJob?.isActive==true || clock()-echoAttempt<20000)return
        echoAttempt=clock();val epoch=echoGeneration
        echoJob=scope.launch {
            try {val result=echoClient.read(f!!,clock());if(epoch==echoGeneration && foreground && (tracker.state.active || canPlan()) && fix?.let { tracker.usable(it) && clock()-it.timestampMs in 0..3000 }==true)mutable.value=mutable.value.copy(echoRisks=result.first,
                echoNotice="제보 ${result.first.count {it.reporters>0}}건 · 역사 구역 ${result.first.count {it.area!=null}}곳 · "+(if(result.second)"사고 다발 자료 연결 · 실시간 사고 확정 아님" else "공식 사고 다발 자료 미연결 · 정보 없음은 안전 보장 아님")+(if(echoClient.coverageLimited)" · 가까운 일부 구역만 표시" else ""))}
            catch(e:Exception){if(e is CancellationException)throw e;if(epoch==echoGeneration)mutable.value=mutable.value.copy(echoNotice="EchoRoad 수신 실패 · 기존 정보는 최대 90초 후 만료")}
        }
    }

    fun configureTraffic(endpoint: String, token: String) {
        if (!canPlan()) return
        trafficClient.configure(endpoint, token)
        trafficGeneration++
        trafficJob?.cancel()
        trafficLastAttempt = 0
        mutable.value =
            mutable.value.copy(
                traffic = null,
                trafficNotice =
                    if (endpoint.isBlank()) "실시간 교통 미연결" else "교통 서버 설정됨 · 현재 위치 주변 1개 도로 구간 조회",
            )
    }

    private fun invalidateTraffic(message:String) {
        trafficGeneration++
        trafficJob?.cancel();trafficJob=null;trafficClient.cancel()
        mutable.value=mutable.value.copy(traffic=null,trafficNotice=message)
    }

    private fun trafficTick() {
        if(!foreground || !engine.state.value.networkAvailable || !engine.state.value.locationAvailable) {
            if(mutable.value.traffic!=null || trafficJob?.isActive==true)
                invalidateTraffic("교통 정보 중지 · 네트워크·위치 연결을 확인하세요.")
            return
        }

        if (mutable.value.traffic?.current(clock()) == false)
            mutable.value = mutable.value.copy(traffic = null, trafficNotice = "교통 정보 만료")
        val f = fix
        if (f == null || !tracker.usable(f)) {
            if (mutable.value.traffic != null)
                mutable.value =
                    mutable.value.copy(traffic = null, trafficNotice = "교통 정보 중지 · 정확한 위치 대기")
            return
        }
        if (
            !foreground ||
                !engine.state.value.networkAvailable ||
                trafficClient.endpoint.isBlank() ||
                trafficJob?.isActive == true ||
                clock() - trafficLastAttempt < 60_000
        )
            return
        trafficLastAttempt = clock()
        val epoch = trafficGeneration
        trafficJob =
            scope.launch {
                try {
                    val flow = trafficClient.fetch(f.point, clock)
                    if (
                        epoch == trafficGeneration &&
                            foreground && engine.state.value.networkAvailable && engine.state.value.locationAvailable &&
                            fix?.let {
                                tracker.usable(it) &&
                                    NavigationGeometry.distance(it.point, f.point) < 500
                            } == true
                    )
                        mutable.value =
                            mutable.value.copy(
                                traffic = flow,
                                trafficNotice =
                                    "© TomTom · 주변 도로 1구간 ${flow.level} · ${flow.speed.toInt()} km/h · 조회 시각 기준, 경로 전체 정보 아님",
                            )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (epoch == trafficGeneration)
                        mutable.value =
                            mutable.value.copy(
                                traffic = null,
                                trafficNotice = "교통 정보 수신 실패 · 한국 구간은 국내 서버 준비 필요",
                            )
                }
            }
    }

    private var voiceGeneration = 0

    init {
        service.language = language
        scope.launch {
            engine.state.collect { state ->
                if (!state.parkedAllowed && !tracker.state.active &&
                    (mutable.value.busy || pendingDestination != null)) {
                    cancelPendingDestination()
                    cancelRequest()
                    mutable.value = mutable.value.copy(notice = "차량 상태가 바뀌어 대기 중인 검색·경로 요청을 취소했습니다. 주차 확인 후 다시 선택해 주세요.")
                }
            }
        }
        scope.launch {
            speech.outputFailure.collect {message ->
                if(message!=null)mutable.value=mutable.value.copy(voiceNotice=message)
            }
        }
    }

    private var lastQuery = ""
    private var resolvedSearchQuery = ""
    private var nearbySearch = false
    private var seenPlaceIds: List<String> = emptyList()
    private var lastFixTimestamp = Long.MIN_VALUE
    private var rejectedFixTimestamp:Long?=null
    private var resumeLocation = false
    private var locationGeneration = 0
    private var requestedAt: Long? = null
    private var receivedAt: Long? = null
    private var listener: LocationListener? = null
    private var pendingDestination: NavPlace? = null
    private var pendingStart = false
    private var fix: NavFix? = null
    private var fixProvider: String? = null
    private var spoken = ""
    private var rerouteFailures = 0

    private fun listenerFor(epoch: Int) =
        object : LocationListener {
            // These callbacks are abstract on Android 10 (our minimum API).
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onLocationChanged(location: Location) {
                if (epoch != locationGeneration || !foreground || !listening) return
                if (
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
                ) {
                    stopLocation()
                    invalidateGps(GpsConnection.DENIED, "위치 권한이 해제됐습니다. 앱 권한 설정을 확인해 주세요.")
                    return
                }
                val f =
                    NavFix(
                        NavPoint(location.latitude, location.longitude),
                        if (location.hasAccuracy()) location.accuracy.toDouble()
                        else Double.POSITIVE_INFINITY,
                        location.time,
                        if (location.hasSpeed()) location.speed.toDouble() else null,
                        if (location.hasBearing()) location.bearing.toDouble() else null,
                    )
                // A coarse Wi-Fi/cell update must not interrupt a fresh precise GPS
                // stream. A poor GPS update itself still invalidates readiness.
                if (
                    location.provider != LocationManager.GPS_PROVIDER &&
                        fix?.let {
                            tracker.usable(it) &&
                                clock() - it.timestampMs <= 3000 &&
                                f.accuracy > it.accuracy * 2
                        } == true
                )
                    return
                val reconnecting = fix == null && rejectedFixTimestamp == null &&
                    mutable.value.gpsConnection == GpsConnection.WAITING
                if (f.timestampMs < lastFixTimestamp ||
                    (f.timestampMs == lastFixTimestamp && rejectedFixTimestamp != f.timestampMs && !reconnecting)) return
                receivedAt = clock()
                if (!tracker.usable(f)) {
                    if(clock()-f.timestampMs in 0..10_000) {
                        lastFixTimestamp=f.timestampMs;rejectedFixTimestamp=f.timestampMs
                    }
                    invalidateGps(GpsConnection.POOR, "위치가 부정확하거나 오래됐습니다. 정확한 GPS를 기다려 주세요.")
                    return
                }
                lastFixTimestamp = f.timestampMs;rejectedFixTimestamp=null
                echoRealFix=location.provider==LocationManager.GPS_PROVIDER && !location.isFromMockProvider
                fix = f
                fixProvider = location.provider
                mutable.value =
                    mutable.value.copy(
                        gpsConnection = GpsConnection.READY,
                        gpsNotice =
                            "${when(location.provider){LocationManager.GPS_PROVIDER->"GPS"
LocationManager.NETWORK_PROVIDER->"네트워크 위치"
else->"기기 위치"}} 확인 · 정확도 약 ${f.accuracy.toInt()}m",
                    )
                if (f.speedMps?.let { it >= 2 } == true) {
                    safetyHeld = true
                    stationary.reset()
                    engine.setNavigationLock(true)
                }
                if (safetyHeld && !tracker.state.active && stationary.accept(f, clock())) {
                    safetyHeld = false
                    stationary.reset()
                    engine.setNavigationLock(false)
                }
                if (tracker.state.active) {
                    tracker.update(f)
                    if(tracker.state.fix!=f) {
                        rejectedFixTimestamp=f.timestampMs
                        invalidateGps(GpsConnection.POOR,"위치가 크게 바뀌었습니다. GPS 재확인 중 · 회전 안내 일시중지",trackerAlreadyRejected=true)
                        return
                    }
                    echoObservation(f,location)
                    publish()
                    guide()
                    reroute()
                } else {
                    publish()
                    if (canPlan()) {
                        pendingDestination?.let {
                            val start = pendingStart
                            pendingDestination = null
                            plan(it, start)
                        }
                    }
                }
            }

            override fun onProviderDisabled(provider: String) {
                if (epoch == locationGeneration && foreground && listening) {
                    if(provider!=fixProvider && fix?.let {tracker.usable(it)}==true)return
                    spoken = ""
                    repeatGuard.clear()
                    invalidateGps(
                        GpsConnection.DISABLED,
                        "위치 제공자가 중지됐습니다. 위치 설정 또는 GPS 다시 연결을 확인해 주세요.",
                    )
                }
            }
        }

    private fun invalidateGps(status: GpsConnection, message: String, trackerAlreadyRejected:Boolean=false) {
        invalidateTraffic("교통 정보 중지 · 정확한 위치 대기")
        val ownVoice = tracker.state.tracking || spoken.isNotEmpty()
        echoGeneration++;echoJob?.cancel();echoClient.cancel();brakeDetector.reset()
        echoRealFix = false
        mutable.value = mutable.value.copy(echoRisks = emptyList(), echoAhead = null)
        fix = null
        fixProvider = null
        stationary.reset()
        // A rejected jump already suspends tracking. Keep its last trusted anchor so
        // another bad fix cannot become a new starting point on the next callback.
        if(!trackerAlreadyRejected)tracker.suspendTracking()
        if (ownVoice) speech.stopSpeaking()
        mutable.value = mutable.value.copy(gpsConnection = status, gpsNotice = message)
        publish()
    }

    private fun publish() {
        val r = tracker.state.route
        val f = fix
        val gap =
            if (r != null && f != null) NavigationGeometry.distance(f.point, r.points.first())
            else 0.0
        mutable.value =
            mutable.value.copy(
                guidance = tracker.state,
                roadSafety = roadSafety.snapshot(tracker.state, f, foreground),
                originAccessMeters = gap,
                originReady =
                    r != null &&
                        f != null &&
                        tracker.usable(f) &&
                        gap <= kotlin.math.max(45.0, f.accuracy * 2),
                gpsAccuracy = f?.accuracy,
                mapFix = f,
                gpsAgeSeconds = f?.let { ((clock() - it.timestampMs) / 1000).coerceAtLeast(0) },
            )
    }

    private fun cancelRequest() {
        generation++
        job?.cancel()
        service.cancel()
        mutable.value = mutable.value.copy(busy = false)
    }

    private fun request(block: suspend (() -> Boolean) -> Unit) {
        cancelRequest()
        val id = generation
        mutable.value = mutable.value.copy(busy = true)
        job =
            scope.launch {
                try {
                    block { id == generation }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (id == generation)
                        mutable.value =
                            mutable.value.copy(
                                notice =
                                    when (e) {
                                        is java.net.SocketTimeoutException ->
                                            "서버 응답 시간이 지났습니다. 연결 상태를 확인해 주세요."
                                        is java.io.IOException ->
                                            "내비게이션 서버에 연결하지 못했습니다. 네트워크·서비스 주소를 확인해 주세요."
                                        is org.json.JSONException -> "경로 서버의 응답 형식을 확인할 수 없습니다."
                                        else -> e.message ?: "연결할 수 없습니다. 네트워크를 확인해 주세요."
                                    }
                            )
                } finally {
                    if (id == generation) mutable.value = mutable.value.copy(busy = false)
                }
            }
    }

    fun canPlan() = foreground && engine.state.value.parkedAllowed && !tracker.state.active

    fun locate() {
        if (!foreground || (!canPlan() && !tracker.state.active && !safetyHeld)) return
        stopLocation()
        requestedAt = null
        receivedAt = null
        invalidateGps(GpsConnection.WAITING, "GPS 수신 중 · 야외에서 정확한 위치를 기다려 주세요.")
        if (
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            invalidateGps(GpsConnection.DENIED, "정확한 위치 권한이 필요합니다. 앱 권한 설정에서 정확한 위치를 허용해 주세요.")
            return
        }
        try {
            val providers =
                manager.getProviders(true).filter {
                    it in
                        listOf(
                            LocationManager.GPS_PROVIDER,
                            LocationManager.NETWORK_PROVIDER,
                            "fused",
                        )
                }
            if (!manager.isLocationEnabled || providers.isEmpty()) {
                invalidateGps(GpsConnection.DISABLED, "기기 위치 서비스를 켜 주세요.")
                return
            }
            val current = listenerFor(++locationGeneration)
            listener = current
            listening = true
            requestedAt = clock()
            // Receive fresh fixes even when the parked vehicle does not move.
            providers.forEach {
                manager.requestLocationUpdates(it, 1000L, 0f, current, Looper.getMainLooper())
            }
            // Reuse only genuine recent precise framework fixes, preserving their original
            // timestamp.
            providers
                .mapNotNull { manager.getLastKnownLocation(it) }
                .filter {
                    it.hasAccuracy() && it.accuracy in 0f..50f && clock() - it.time in 0..10_000
                }
                .maxByOrNull { it.time }
                ?.let { current.onLocationChanged(it) }
        } catch (_: SecurityException) {
            stopLocation()
            invalidateGps(GpsConnection.DENIED, "위치 권한을 확인해 주세요.")
        } catch (_: IllegalArgumentException) {
            stopLocation()
            invalidateGps(GpsConnection.DISABLED, "위치 제공자를 사용할 수 없습니다. 위치 설정을 확인해 주세요.")
        }
    }

    fun search(query: String, nearby: Boolean = false, more: Boolean = false) {
        if (!canPlan()) return
        if (query.isBlank()) {
            mutable.value = mutable.value.copy(notice = "검색어 또는 위도,경도 좌표를 입력해 주세요.")
            return
        }
        val coordinates = query.split(',').map { it.trim().toDoubleOrNull() }
        val coordinateSearch = !more && coordinates.size == 2 && coordinates.all { it != null }
        if (!coordinateSearch && !engine.state.value.networkAvailable) {
            mutable.value = mutable.value.copy(notice = "장소 검색에는 인터넷 연결이 필요합니다. 기존 결과는 유지합니다.")
            return
        }
        if (more && (!mutable.value.hasMore || query != lastQuery || nearby != nearbySearch)) return
        val center = fix?.takeIf { tracker.usable(it) }?.point
        if (nearby && center == null) {
            mutable.value = mutable.value.copy(notice = "주변 가게 검색은 내 위치를 먼저 받아 주세요.")
            return
        }
        val old = if (more) mutable.value.places else emptyList()
        if (!more) {
            pendingStart = false
            pendingDestination = null
            resolvedSearchQuery = query
            cancelRequest()
            seenPlaceIds = emptyList()
            tracker.stop()
            publish()
            mutable.value =
                mutable.value.copy(places = emptyList(), selectedPlace = null, hasMore = false)
        }
        mutable.value = mutable.value.copy(searchQuery = query)
        lastQuery = query
        nearbySearch = nearby
        if (coordinateSearch) {
            val p = NavPoint(coordinates[0]!!, coordinates[1]!!)
            mutable.value =
                mutable.value.copy(
                    places =
                        if (p.valid())
                            listOf(
                                NavPlace(
                                    "좌표 목적지 ${p.latitude}, ${p.longitude}",
                                    p,
                                    source = "좌표 입력",
                                )
                            )
                        else emptyList(),
                    hasMore = false,
                    notice = "좌표는 위도,경도 순서입니다.",
                )
            return
        }
        request { current ->
            var page =
                service.search(
                    if (more) resolvedSearchQuery else query,
                    center,
                    nearby,
                    seenPlaceIds,
                )
            if (!current() || !canPlan()) return@request
            val suggestion =
                if (!more && page.places.isEmpty() && page.seenIds.isEmpty())
                    PlaceSearch.spacingSuggestion(query)
                else null
            if (suggestion != null) {
                page = service.search(suggestion, center, nearby, seenPlaceIds)
                if (!current() || !canPlan()) return@request
                resolvedSearchQuery = suggestion
            }
            if (current() && canPlan()) {
                val spacingNotice =
                    if (resolvedSearchQuery != query) "띄어쓰기를 나눠 검색했습니다: $resolvedSearchQuery · "
                    else ""
                seenPlaceIds = page.seenIds
                val result = page.places
                val combined =
                    (old + result)
                        .distinctBy { it.id.ifBlank { it.name + it.point } }
                        .take(PlaceSearch.maxResults)
                mutable.value =
                    mutable.value.copy(
                        places = combined,
                        hasMore = page.hasMore && combined.size < PlaceSearch.maxResults,
                        notice =
                            spacingNotice +
                                (if (result.isEmpty() && page.hasMore)
                                    "이번 결과에 20km 내 가게가 없습니다. 다른 결과 더 보기를 눌러 확인하세요."
                                else if (result.isEmpty())
                                    "일치하는 검색 결과를 찾지 못했습니다. 가게명·지점·도시나 현지어를 바꿔 보세요. 누락된 가게가 있을 수 있습니다."
                                else "가게명·주소·출처를 확인하고 선택하세요. 검색 결과는 전체 가게 목록이 아닙니다."),
                    )
                if (engine.state.value.preferences.voiceEnabled && combined.isNotEmpty())
                    speech.speakNavigation(
                        combined
                            .take(6)
                            .mapIndexed { i, p -> "${i+1}. ${p.name}" }
                            .joinToString(". "),
                        language,
                    )
            }
        }
    }

    fun searchRegional(query: String) {
        if (
            !canPlan() ||
                mutable.value.busy ||
                !engine.state.value.networkAvailable ||
                RegionalPlaceSearch.splits(query).isEmpty()
        )
            return
        val center = fix?.takeIf { tracker.usable(it) }?.point
        cancelPendingDestination()
        cancelRequest()
        tracker.stop()
        publish()
        lastQuery = query
        seenPlaceIds = emptyList()
        mutable.value =
            mutable.value.copy(
                places = emptyList(),
                selectedPlace = null,
                hasMore = false,
                notice = "지역과 상호를 나눠 주변 후보를 확인합니다…",
            )
        request { current ->
            val result = service.regionalSearch(query, center)
            if (current() && canPlan()) {
                mutable.value =
                    mutable.value.copy(
                        places = result.places,
                        hasMore = false,
                        notice = result.notice,
                    )
                if (engine.state.value.preferences.voiceEnabled && result.places.isNotEmpty())
                    speech.speakNavigation(
                        "주변 후보입니다. 주소와 지점을 확인하세요. " +
                            result.places
                                .mapIndexed { i, p -> "${i+1}. ${p.name}" }
                                .joinToString(". "),
                        language,
                    )
            }
        }
    }

    fun navigateTo(destination: NavPlace) = plan(destination, true)

    fun ensureLocation() {
        if (
            fix?.let { tracker.usable(it) } != true &&
                (!listening || mutable.value.gpsConnection != GpsConnection.WAITING)
        )
            locate()
    }

    fun plan(destination: NavPlace, startWhenReady: Boolean = false) {
        if (!canPlan()) {
            mutable.value = mutable.value.copy(notice = "안전한 정차 확인 또는 진행 중인 안내 종료 후 목적지를 설정하세요.")
            return
        }
        pendingStart = startWhenReady
        pendingDestination = null
        cancelRequest()
        tracker.stop()
        publish()
        mutable.value = mutable.value.copy(selectedPlace = destination)
        if (!engine.state.value.networkAvailable) {
            mutable.value =
                mutable.value.copy(notice = "선택한 지점 위치는 표시합니다. 새 자동차 경로는 온라인 연결이 필요합니다.")
            return
        }
        val f = fix
        if (f == null || !tracker.usable(f)) {
            pendingDestination = destination
            mutable.value =
                mutable.value.copy(notice = "선택한 지점을 표시했습니다. 내 위치를 허용하면 앱에서 자동차 경로를 계산합니다.")
            ensureLocation()
            return
        }
        mutable.value = mutable.value.copy(notice = "현재 위치에서 ${destination.name}까지 자동차 경로 계산 중…")
        request { current ->
            val r = service.route(f.point, destination)
            if (current() && canPlan()) {
                tracker.preview(r.localized(language))
                publish()
                if (!pendingStart && engine.state.value.preferences.voiceEnabled)
                    speech.speakNavigation(destination.name, language)
                mutable.value =
                    mutable.value.copy(
                        busy = false,
                        notice = "경로를 확인한 뒤 안내 시작을 누르세요. 실제 교통·차선 정보는 제공하지 않습니다.",
                    )
                val begin = pendingStart
                pendingStart = false
                if (begin) start()
            }
        }
    }

    fun start(accessConfirmed: Boolean = false) {
        if (!canPlan() || mutable.value.busy) return
        if (
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            stopLocation()
            invalidateGps(GpsConnection.DENIED, "정확한 위치 권한을 확인해 주세요.")
            return
        }
        if ((tracker.state.route?.destinationAccessMeters ?: 0.0) > 100 && !accessConfirmed) {
            mutable.value = mutable.value.copy(notice = "차량 접근 지점과 목적지의 차이를 먼저 확인해 주세요.")
            return
        }
        val f = fix
        if (f == null || !tracker.start(f)) {
            mutable.value =
                mutable.value.copy(notice = "출발 위치의 정확한 GPS가 필요합니다. 이동했다면 경로를 다시 계산하세요.")
            return
        }
        cancelRequest()
        mutable.value = mutable.value.copy(notice = "현재 위치에서 안내 중입니다. 실제 교통·차선 정보는 제공하지 않습니다.")
        safetyHeld = true
        stationary.reset()
        engine.setNavigationLock(true)
        publish()
        spoken = ""
        repeatGuard.clear()
        rerouteFailures = 0
        safetyAnnouncements.reset()
        guide()
    }

    private fun guide() {
        val g = tracker.state
        if (
            !g.tracking ||
                g.offRoute ||
                g.directionUncertain ||
                !engine.state.value.preferences.voiceEnabled
        ) {
            speech.stopSpeaking()
            return
        }
        val band =
            when {
                g.arrived -> "arrived"
                g.turnMeters < 60 -> "now"
                g.turnMeters < 300 -> "soon"
                else -> "ahead"
            }
        val key = "${g.maneuverIndex}|${g.nextInstruction}|$band"
        if (key != spoken && !mutable.value.voiceListening && repeatGuard.allow(g, clock())) {
            val code = NavigationLanguages.resolve(language)
            val spokenLocale = NavigationSpeechVoice.locale(language)
            val distance = NavigationSpeechVoice.distance(g.turnMeters,language)
            val maneuver=g.route?.steps?.getOrNull(g.maneuverIndex)?.maneuver
            if (
                speech.speakNavigation(
                    if (g.arrived) g.nextInstruction
                    else if(maneuver!=null) NavigationSpeech.guidance(maneuver,code,distance,g.turnMeters<=20)
                    else NavigationLanguages.spoken(code, distance, g.nextInstruction),
                    spokenLocale.toLanguageTag(),
                    requestKey=key,
                )
            ) {
                spoken = key
                repeatGuard.accepted(g)
            }
            mutable.value = mutable.value.copy(voiceNotice = speech.status)
        }
    }

    private fun guideRoadSafety() {
        val g = tracker.state
        if (
            !foreground ||
                !engine.state.value.preferences.voiceEnabled ||
                mutable.value.voiceListening ||
                speech.navigationSpeaking
        )
            return
        val approach =
            mutable.value.roadSafety.upcoming.firstOrNull {
                safetyAnnouncements.eligible(it, clock(), g.turnMeters)
            } ?: return
        val korean = NavigationLanguages.resolve(language) == "ko"
        val text = approach.spoken(korean)
        if (speech.speakNavigation(text, if (korean) "ko-KR" else "en-US")) {
            safetyAnnouncements.accepted(approach, clock())
        }
    }

    private fun reroute():Boolean {
        if (
            !foreground ||
                !engine.state.value.networkAvailable ||
                mutable.value.busy ||
                rerouteFailures >= 3 ||
                !tracker.takeReroute()
        )
            return false
        val g = tracker.state
        val destination = g.route?.destination ?: return false
        val origin = g.fix?.point ?: return false
        request { current ->
            try {
                val r = service.route(origin, destination)
                if (
                    current() &&
                        foreground &&
                        tracker.state.active &&
                        tracker.replaceRoute(r.localized(language))
                ) {
                    spoken = ""
                    rerouteFailures = 0
                    publish()
                    guide()
                    mutable.value = mutable.value.copy(notice = "경로를 다시 계산했습니다.")
                }
            } catch (e: Exception) {
                if (e !is CancellationException) rerouteFailures++
                throw e
            }
        }
        return true
    }

    fun tick() {
        val wasTracking = tracker.state.tracking
        tracker.tick()
        if (wasTracking && !tracker.state.tracking) {
            speech.stopSpeaking()
        }
        if (listening) {
            if (
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                stopLocation()
                invalidateGps(GpsConnection.DENIED, "위치 권한이 해제됐습니다. 앱 권한 설정을 확인해 주세요.")
            } else if (
                mutable.value.gpsConnection != GpsConnection.STALE &&
                    (fix?.let { !tracker.usable(it) } == true ||
                        fix == null && receivedAt?.let { clock() - it > 10_000 } == true ||
                        fix == null &&
                            receivedAt == null &&
                            requestedAt?.let { clock() - it >= 15_000 } == true)
            )
                invalidateGps(GpsConnection.STALE, "GPS 응답이 오래됐습니다. 다시 연결하거나 위치 설정을 확인해 주세요.")
        }
        publish()
        resumeLocationIfReady()
        trafficTick()
        guideRoadSafety()
        echoTick()
        if (tracker.state.active) reroute()
    }

    fun configure(search: String, route: String, accessToken: String = "") {
        if (canPlan()) {
            cancelPendingDestination()
            cancelRequest()
            service.configure(search, route, accessToken)
            tracker.stop()
            mutable.value =
                mutable.value.copy(
                    guidance = tracker.state,
                    places = emptyList(),
                    selectedPlace = null,
                    hasMore = false,
                    originReady = false,
                    notice = "검색 서버 설정을 적용했습니다. 가게를 다시 검색하세요.",
                )
        }
    }

    private fun cancelPendingDestination() {
        pendingStart = false
        pendingDestination = null
    }

    fun cancelPending() {
        val waitingForGps = pendingDestination != null
        cancelPendingDestination()
        if (canPlan() && (mutable.value.busy || waitingForGps)) {
            cancelRequest()
            mutable.value = mutable.value.copy(notice = "요청을 취소했습니다. 검색어나 목적지를 바꿀 수 있습니다.")
        }
    }

    fun retry() {
        if(!foreground)return
        val g=tracker.state
        val wait=tracker.rerouteDelaySeconds()
        val reason=when {
            !g.active -> "안내를 시작한 뒤 재탐색할 수 있습니다."
            !engine.state.value.networkAvailable -> "재탐색에는 인터넷 연결이 필요합니다. 연결 후 다시 시도해 주세요."
            mutable.value.busy -> "이미 경로를 확인 중입니다. 결과를 기다려 주세요."
            !g.tracking || g.fix?.let {tracker.usable(it)}!=true -> "정확한 GPS를 확인한 뒤 재탐색할 수 있습니다."
            !g.offRoute -> "현재 안내 경로 위입니다. 재탐색이 필요하지 않습니다."
            wait>0 -> "서버 요청 간격을 지키기 위해 ${wait}초 후 재탐색할 수 있습니다."
            else -> null
        }
        if(reason!=null) {mutable.value=mutable.value.copy(notice=reason);return}
        rerouteFailures = 0
        if(!reroute())mutable.value=mutable.value.copy(notice="새 GPS에서 경로 이탈을 다시 확인한 뒤 재탐색합니다.")
    }

    fun stop() {
        invalidateTraffic("교통 정보 중지 · 안내 종료")
        echoGeneration++;echoJob?.cancel();echoClient.cancel();brakeDetector.reset();echoSpoken.clear()
        mutable.value=mutable.value.copy(echoRisks=emptyList(),echoAhead=null)
        pendingStart = false
        pendingDestination = null
        cancelVoice()
        cancelRequest()
        speech.stopSpeaking()
        tracker.stop()
        repeatGuard.clear()
        stationary.reset()
        if (!safetyHeld) {
            resumeLocation = false
            engine.setNavigationLock(false)
            stopLocation()
            invalidateGps(GpsConnection.OFF, "GPS 미연결 · 내 위치 사용을 눌러 주세요.")
        }
        publish()
        mutable.value =
            mutable.value.copy(
                places = emptyList(),
                selectedPlace = null,
                hasMore = false,
                notice = "안내를 종료했습니다. 정차 상태를 다시 확인합니다.",
            )
    }

    private fun stopLocation() {
        locationGeneration++
        listener?.let { runCatching { manager.removeUpdates(it) } }
        listener = null
        listening = false
    }

    fun setForeground(active: Boolean) {
        if (!active) {
            val pending = pendingDestination != null || mutable.value.busy && pendingStart
            cancelPendingDestination()
            if (pending) mutable.value = mutable.value.copy(notice = "화면을 떠나 대기 중인 안내 시작을 취소했습니다. 목적지를 다시 선택해 주세요.")
            cancelEchoCheck()
            echoGeneration++;echoJob?.cancel();echoClient.cancel();brakeDetector.reset()
            mutable.value=mutable.value.copy(echoRisks=emptyList(),echoAhead=null)
            trafficGeneration++
            trafficJob?.cancel()
            trafficClient.cancel()
            mutable.value =
                mutable.value.copy(
                    traffic = null,
                    trafficNotice =
                        if (trafficClient.endpoint.isBlank()) "실시간 교통 미연결"
                        else "교통 정보 중지 · 화면 복귀 후 새로 조회",
                )
        }
        foreground = active
        if (!active)
            mutable.value =
                mutable.value.copy(
                    roadSafety = RoadSafetyUi(notice = mutable.value.roadSafety.notice)
                )
        if (!active) cancelVoice()
        if (!active) {
            resumeLocation = resumeLocation || listening
            cancelRequest()
            stopLocation()
            invalidateGps(GpsConnection.OFF, "화면 밖에서는 위치 수신 중지")
        } else {
            if (tracker.state.active || safetyHeld) resumeLocation = true
            resumeLocationIfReady()
        }
    }

    private fun resumeLocationIfReady() {
        if (foreground && resumeLocation && (canPlan() || tracker.state.active || safetyHeld)) {
            resumeLocation = false
            locate()
        }
    }

    fun openLocationSettings(appPermissions: Boolean = false) {
        if (!canPlan()) return
        val intent =
            if (appPermissions)
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}"),
                )
            else android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        resumeLocation = true
        runCatching {
                context.startActivity(
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .onFailure {
                resumeLocation = false
                mutable.value =
                    mutable.value.copy(gpsNotice = "설정 화면을 열 수 없습니다. 기기 설정에서 위치·앱 권한을 확인해 주세요.")
            }
    }

    fun setLanguage(tag: String) {
        if (!canPlan() || !NavigationLanguages.validTag(tag)) return
        cancelPendingDestination()
        cancelVoice()
        cancelRequest()
        language = tag
        preferences.navigationLanguage = tag
        service.language = tag
        service.clearCache()
        tracker.state.route?.let { tracker.preview(it.localized(tag)) }
        spoken = ""
        publish()
        mutable.value =
            mutable.value.copy(
                language = tag,
                places = emptyList(),
                hasMore = false,
                voiceNotice =
                    if (NavigationLanguages.resolve(tag) == "en" && !tag.startsWith("en", true))
                        "선택한 언어의 안내 번역 없음 · 영어 안내로 대체. 목적지 인식은 선택 언어 사용."
                    else "언어 적용 · 기기 음성팩이 필요합니다.",
            )
    }

    fun availableVoiceLanguages() = speech.navigationLanguages()

    fun voiceError(message: String) {
        mutable.value = mutable.value.copy(voiceListening = false, voiceNotice = message)
    }

    fun cancelVoice() {
        voiceGeneration++
        speech.cancelRecognition()
        mutable.value = mutable.value.copy(voiceListening = false)
    }

    fun listenDestination() {
        if (!foreground) return
        cancelVoice()
        val epoch = voiceGeneration
        mutable.value =
            mutable.value.copy(voiceListening = true, voiceNotice = "듣고 있습니다 · 목적지 또는 짧은 명령을 말하세요.")
        speech.listenNavigation(
            language,
            { raw ->
                if (epoch == voiceGeneration && foreground) {
                    mutable.value = mutable.value.copy(voiceListening = false)
                    handleVoice(raw)
                }
            },
            { error -> if (epoch == voiceGeneration) voiceError(error) },
        )
    }

    fun handleVoice(raw: String) {
        if (!foreground) return
        val c = NavigationVoice.parse(raw)
        mutable.value = mutable.value.copy(voiceNotice = "인식: ${raw.take(180)}")
        when (c.action) {
            NavigationVoiceAction.REPEAT -> {
                spoken = ""
                repeatGuard.clear()
                guide()
            }
            NavigationVoiceAction.STOP -> stop()
            NavigationVoiceAction.CANCEL -> {
                cancelPending()
                cancelVoice()
            }
            else -> {
                if (!canPlan()) {
                    voiceError("새 목적지·결과 선택·안내 시작은 안전한 주차 확인 후 가능합니다. 주행 중에는 안내 반복·종료를 말하세요.")
                    return
                }
                when (c.action) {
                    NavigationVoiceAction.SEARCH ->
                        if (c.query.isNotBlank()) {
                            ensureLocation()
                            search(c.query)
                        } else voiceError("목적지를 조금 더 자세히 말씀해 주세요.")
                    NavigationVoiceAction.REGIONAL -> searchRegional(lastQuery)
                    NavigationVoiceAction.SELECT ->
                        mutable.value.places.getOrNull(c.index)?.let { navigateTo(it) }
                            ?: voiceError("검색 결과에 있는 번호를 말씀해 주세요.")
                    NavigationVoiceAction.START ->
                        if (tracker.state.route == null || mutable.value.busy)
                            voiceError("먼저 결과 번호를 말하고 경로 계산을 기다려 주세요.")
                        else start()
                    else -> Unit
                }
            }
        }
    }

    fun clearSelection() {
        if (canPlan()) {
            pendingStart = false
            pendingDestination = null
            cancelRequest()
            tracker.stop()
            publish()
            mutable.value = mutable.value.copy(selectedPlace = null, notice = "선택한 장소를 지웠습니다.")
        }
    }

    fun openPlaceWebsite(place: NavPlace) {
        if (!canPlan() || place.website.isBlank()) return
        val uri = android.net.Uri.parse(place.website)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return
        runCatching {
                context.startActivity(
                    android.content
                        .Intent(android.content.Intent.ACTION_VIEW, uri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .onFailure { mutable.value = mutable.value.copy(notice = "상세정보 페이지를 열 수 없습니다.") }
    }

    fun externalSearch(query: String) {
        if (!canPlan() || query.isBlank()) return
        val uri =
            android.net.Uri.parse(
                "https://www.google.com/maps/search/?api=1&query=" +
                    android.net.Uri.encode(query.take(180))
            )
        runCatching {
                context.startActivity(
                    android.content
                        .Intent(android.content.Intent.ACTION_VIEW, uri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .onFailure {
                mutable.value = mutable.value.copy(notice = "외부 지도를 열 수 없습니다. 브라우저 설치를 확인하세요.")
            }
    }

    fun clear() {
        cancelEchoCheck();echoProbe.configure("","")
        echoClient.configure("","");echoSharing=false
        trafficGeneration++
        trafficJob?.cancel()
        trafficClient.configure("", "")
        cancelVoice()
        language = "ko-KR"
        preferences.navigationLanguage = language
        service.language = language
        stop()
        service.clearPersonalData()
        lastQuery = ""
        resolvedSearchQuery = ""
        nearbySearch = false
        seenPlaceIds = emptyList()
        mutable.value = NavigationUi(notice = "검색·좌표·접근키를 삭제했습니다.")
    }

    fun close() {
        repeatGuard.clear()
        cancelEchoCheck();echoProbe.configure("","")
        echoJob?.cancel();echoClient.configure("","")
        trafficJob?.cancel()
        trafficClient.cancel()
        cancelVoice()
        cancelRequest()
        stopLocation()
        scope.cancel()
    }
}
