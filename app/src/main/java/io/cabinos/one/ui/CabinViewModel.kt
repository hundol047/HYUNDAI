package io.cabinos.one.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cabinos.core.*
import io.cabinos.one.data.*
import io.cabinos.one.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class CabinViewModel @Inject constructor(
    val engine:UnifiedVehicleContextEngine,
    private val simulator:SimulationVehicleProvider,
    private val dao:CabinDao,
    val speech:AndroidSpeechProvider,
    private val location:AndroidLocationProvider,
    private val navigation:AndroidNavigationProvider,
    private val audio:AmbientAudio,
    private val reminders:DepartureReminders,
    private val nearbyPlaces:NearbyPlaces,
    val liveNavigation:LiveNavigation,
    private val runtimePreferences:RuntimePreferences,
):ViewModel() {
    private val mutableAppearance=MutableStateFlow(runtimePreferences.appearance)
    val appearance=mutableAppearance.asStateFlow()
    fun appearance(value:AppearanceSettings){if(state.value.parkedAllowed){val normalized=AppearanceCatalog.normalize(value);runtimePreferences.appearance=normalized;mutableAppearance.value=normalized}}
    val state=engine.state
    val tasks=dao.tasks().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val mutableReply=MutableStateFlow("안녕하세요. 이동의 모든 순간을 함께할게요.")
    val reply=mutableReply.asStateFlow()
    private val mutableScenario=MutableStateFlow(simulator.selected)
    val scenario=mutableScenario.asStateFlow()
    private val mutableAudio=MutableStateFlow<String?>(null)
    val audioPreset=mutableAudio.asStateFlow()
    private val mutableDemoRunning=MutableStateFlow(false)
    val demoRunning=mutableDemoRunning.asStateFlow()
    private var demoJob:Job?=null
    private val mutableNearbyStatus=MutableStateFlow("예시 장소 · 실제 지도 위의 데모 핀입니다.")
    val nearbyStatus=mutableNearbyStatus.asStateFlow()
    private val mutableNearbyBusy=MutableStateFlow(false)
    val nearbyBusy=mutableNearbyBusy.asStateFlow()
    private val mutableMapSelection=MutableStateFlow<String?>(null)
    val mapSelection=mutableMapSelection.asStateFlow()
    private var nearbyJob:Job?=null
    private var lastNearbySearch=0L
    private var nearbyGeneration=0
    private fun cancelNearby(){nearbyGeneration++;nearbyJob?.cancel();mutableNearbyBusy.value=false}
    fun mapUnavailable(){mutableNearbyStatus.value="지도 앱이나 브라우저가 설치되어 있지 않습니다."}
    private var foreground = true
    private val persistenceMutex = Mutex()
    private val mutableStorageStatus = MutableStateFlow<String?>(null)
    val storageStatus = mutableStorageStatus.asStateFlow()
    init {
        viewModelScope.launch {
            val checkpoint=dao.checkpoint()
            engine.restore(dao.preferences()?.domain() ?: Preferences(),checkpoint?.activeSessionId?.let { dao.session(it)?.domain() },dao.history().map { it.domain() },checkpoint?.departure)
            launch { location.available.collect { available -> if (simulator.realMode) engine.setConnectivity(location=available) } }
            launch { simulator.samples.collect { if (foreground) engine.acceptVehicle(it) } }
            launch { while(isActive) {
                delay(1000)
                if (foreground) { simulator.heartbeat();engine.tick();liveNavigation.tick() }
                if (!audio.playing) mutableAudio.value=null
            } }
            launch {
                var fingerprint:Any?=null
                state.collect { s ->
                    if(!s.parkedAllowed || s.session?.status!=SessionStatus.RUNNING) { audio.stop();mutableAudio.value=null }
                    if(!s.parkedAllowed || !s.networkAvailable || !s.locationAvailable) {
                        cancelNearby()
                    }
                    reminders.sync(s)
                    persistenceMutex.withLock {
                        val latest = state.value
                        val f = listOf(latest.preferences,latest.session,latest.history,latest.schedule.departureAtMs)
                        if(f != fingerprint) {
                            try { dao.saveState(latest);fingerprint=f;mutableStorageStatus.value=null }
                            catch (error: Exception) { if (error is CancellationException) throw error;mutableStorageStatus.value="저장 공간을 확인해 주세요. 최근 변경 사항을 저장하지 못했습니다." }
                        }
                    }
                }
            }
            launch {
                var lastId=0L
                state.collect { s ->
                    val audible=setOf("RoadHazardDetected","DepartureApproaching","SessionCompleted","SessionSafelySuspended")
                    s.events.filter { it.id>lastId && it.name in audible }.lastOrNull()?.let { if(foreground && s.preferences.voiceEnabled) speech.speak(it.message) }
                    lastId=s.events.lastOrNull()?.id ?: lastId
                }
            }
        }
    }
    fun selectScenario(name:String) {
        simulator.select(name);mutableScenario.value=name
        if(name=="실차 연결") { location.start();engine.setConnectivity(location=false);mutableReply.value="Pleos SDK가 연결되지 않았습니다. 주차 확인 전까지 안전 제한을 유지합니다." }
        else { location.stop();engine.setConnectivity(location=true) }
    }
    fun waiting(minutes:Int) { engine.setWaitingMinutes(minutes) }
    fun start(kind:SessionKind,minutes:Int) { engine.stayInCabin();engine.startSession(kind,minutes) }
    fun command(text:String) { mutableReply.value=engine.assistant(text);if(state.value.preferences.voiceEnabled) speech.speak(mutableReply.value) }
    fun listen() { speech.listen { text ->
        if(text.contains("권한") || text.contains("서비스가 없습니다") || text.contains("완료되지")) mutableReply.value=text else command(text)
    } }
    private fun writeTask(action:suspend ()->Unit) {
        if (!state.value.parkedAllowed) return
        viewModelScope.launch {
            persistenceMutex.withLock {
                if (!state.value.parkedAllowed) return@withLock
                try { action() }
                catch(error:Exception) { if(error is CancellationException)throw error;mutableStorageStatus.value="할 일 변경 사항을 저장하지 못했습니다. 저장 공간을 확인해 주세요." }
            }
        }
    }
    fun addTask(text:String) { if(text.isNotBlank()) writeTask { dao.saveTask(TaskRecord(UUID.randomUUID().toString(),text.trim().take(100))) } }
    fun toggleTask(task:TaskRecord) = writeTask { dao.saveTask(task.copy(done=!task.done)) }
    fun deleteTask(task:TaskRecord) = writeTask { dao.deleteTask(task.id) }
    fun preferences(p:Preferences) { engine.updatePreferences(p) }
    fun toggleAudio(preset:String) {
        if(!state.value.parkedAllowed || state.value.session?.status!=SessionStatus.RUNNING)return
        if(mutableAudio.value==preset) {audio.stop();mutableAudio.value=null} else {if(audio.play(preset)) mutableAudio.value=preset else mutableReply.value="다른 오디오 또는 기기 상태로 배경 소리를 재생할 수 없습니다."}
    }
    fun setForeground(active: Boolean) {
        liveNavigation.setForeground(active)
        if (foreground == active) return
        foreground=active
        if (!active) {
            demoJob?.cancel();mutableDemoRunning.value=false
            audio.stop();mutableAudio.value=null;speech.cancelListening()
            engine.invalidateTelemetry("화면을 벗어나 세션을 일시정지했습니다. 복귀 후 차량 상태를 다시 확인합니다.")
            location.stop();cancelNearby()
        } else {
            simulator.heartbeat()
            engine.acceptVehicle(simulator.samples.value)
            if (simulator.realMode) location.start()
        }
    }
    fun mapSelect(id:String) { if(state.value.parkedAllowed)mutableMapSelection.value=id }
    fun mapOrigin(position:MapPosition) {
        if(!foreground || !state.value.parkedAllowed || !position.valid())return
        cancelNearby();mutableMapSelection.value=null
        engine.updateNearbyActivities(position,emptyList())
        mutableNearbyStatus.value="기준점이 바뀌었습니다. 주변 검색을 눌러 실제 등록 장소를 불러오세요."
    }
    fun mapExamples() {
        if(!state.value.parkedAllowed)return
        cancelNearby();mutableMapSelection.value=null
        engine.updateNearbyActivities(MapPosition(),DemoActivityRepository().activities())
        mutableNearbyStatus.value="예시 장소 · 실제 지도 위의 데모 핀입니다."
    }
    fun mapLocate() {
        if(!foreground || !state.value.parkedAllowed || !state.value.locationAvailable)return
        cancelNearby();val generation=nearbyGeneration;mutableNearbyBusy.value=true;mutableNearbyStatus.value="기기 위치 확인 중…"
        nearbyJob=viewModelScope.launch {
            try {
                val position=nearbyPlaces.locate()
                if(foreground && state.value.parkedAllowed && state.value.locationAvailable) {
                    if(position!=null) {engine.updateNearbyActivities(position,emptyList());mutableMapSelection.value=null;mutableNearbyStatus.value="위치를 확인했습니다. 주변 검색을 눌러 주세요."}
                    else mutableNearbyStatus.value="현재 위치를 얻지 못했습니다. 위치 권한·GPS를 확인하거나 지도 중심을 사용하세요."
                }
            } finally { if(generation==nearbyGeneration)mutableNearbyBusy.value=false }
        }
    }
    fun searchNearby() {
        if(!foreground || !state.value.parkedAllowed || !state.value.networkAvailable || !state.value.locationAvailable || mutableNearbyBusy.value)return
        val now=System.currentTimeMillis()
        if(now-lastNearbySearch in 0..29_999) {mutableNearbyStatus.value="검색은 30초 간격으로 가능합니다. 잠시 후 다시 시도해 주세요.";return}
        lastNearbySearch=now;val origin=state.value.activityOrigin
        cancelNearby();val generation=nearbyGeneration
        mutableNearbyBusy.value=true;mutableNearbyStatus.value="OpenStreetMap 주변 장소 검색 중…"
        nearbyJob=viewModelScope.launch {
            try {
                val places=nearbyPlaces.search(origin)
                if(foreground && state.value.parkedAllowed && state.value.networkAvailable && state.value.locationAvailable && state.value.activityOrigin==origin) {
                    engine.updateNearbyActivities(origin,places);mutableMapSelection.value=places.firstOrNull()?.id
                    mutableNearbyStatus.value=if(places.isEmpty())"이 기준점 주변에 등록된 장소가 없습니다. 지도 중심을 바꿔 검색해 보세요." else "OpenStreetMap 등록 장소 ${places.size}곳 · 영업 여부와 실제 경로는 외부 지도에서 확인하세요."
                }
            } catch(error:Exception) {
                if(error is CancellationException)throw error
                mutableNearbyStatus.value="장소 검색에 연결하지 못했습니다. 기존 표시를 유지합니다. 네트워크를 확인해 주세요."
            } finally { if(generation==nearbyGeneration)mutableNearbyBusy.value=false }
        }
    }
    fun notificationsAllowed() = reminders.permitted()
    fun refreshNotifications() { reminders.sync(state.value) }
    fun requestLocation() { if (simulator.realMode) location.start() }
    fun clearPersonalData() {
        if (!state.value.parkedAllowed) return
        viewModelScope.launch {
            persistenceMutex.withLock {
                if (!engine.clearPersonalData()) return@withLock
                runtimePreferences.appearance=AppearanceSettings();mutableAppearance.value=AppearanceSettings()
                demoJob?.cancel();mutableDemoRunning.value=false
                audio.stop();mutableAudio.value=null;reminders.cancel();cancelNearby();mutableMapSelection.value=null;liveNavigation.clear()
                try { dao.clearPersonalData();dao.saveState(state.value);mutableStorageStatus.value="할 일·기록·개인 설정을 삭제했습니다." }
                catch (error: Exception) { if(error is CancellationException) throw error;mutableStorageStatus.value="데이터 삭제를 완료하지 못했습니다. 다시 시도해 주세요." }
            }
        }
    }
    fun navigation() { if(state.value.sample?.source!=DataSource.VEHICLE || !navigation.resumeNavigation()) mutableReply.value="사용 가능한 실제 위치 또는 지도 앱이 없습니다. Pleos 경로 연동은 지원 확인 후 연결해야 합니다." }
    fun runDemo() {
        if(mutableDemoRunning.value) { demoJob?.cancel();mutableDemoRunning.value=false;return }
        demoJob=viewModelScope.launch {
            mutableDemoRunning.value=true
            try {
                selectScenario("주행");delay(3500)
                selectScenario("신호 대기");delay(2500)
                selectScenario("주차");delay(2500)
                selectScenario("충전");delay(2500)
                start(SessionKind.FOCUS,25);delay(3500)
                selectScenario("충전 15분");delay(3500)
                start(SessionKind.RELAX,5);delay(3500)
                selectScenario("출발 준비");delay(3500)
                selectScenario("주행")
            } finally { mutableDemoRunning.value=false }
        }
    }
    override fun onCleared() { audio.stop();location.stop();liveNavigation.setForeground(false);speech.cancelListening();super.onCleared() }
}
