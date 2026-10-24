package io.cabinos.one.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VehicleNavigationScreen { MAP, SEARCH, CHARGERS, RECENT }
data class PleosNavigationState(val included: Boolean, val connected: Boolean = false, val message: String,
    val destination: String? = null, val remainingSeconds: Int? = null, val remainingMeters: Int? = null,
    val readAtMs: Long? = null)
internal data class DestinationEstimate(val name: String, val seconds: Int, val meters: Int)
internal interface PleosNavigationClient {
    fun initialize(connected: () -> Unit, failed: (Throwable) -> Unit, error: (String) -> Unit)
    fun open(screen: VehicleNavigationScreen, keyword: String)
    fun destinationListener(listener: (DestinationEstimate?) -> Unit) {}
    fun requestDestination() {}
    fun release()
}

/** Hand off to the vehicle's navigation app. Never invent a route from initialize()/a sent request. */
class PleosNavigation internal constructor(
    private val factory: (() -> PleosNavigationClient)?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(PleosNavigationState(factory!=null,message=if(factory==null) "NaviHelper SDK 미포함" else "Pleos 내비게이션 연결 대기"))
    val state = mutable.asStateFlow()
    private var client: PleosNavigationClient? = null
    private var generation = 0
    private var timeout: Job? = null
    private var poll: Job? = null
    private fun clearDestination() {
        mutable.value=state.value.copy(destination=null,remainingSeconds=null,remainingMeters=null,readAtMs=null)
    }
    fun start() {
        if(factory==null || client!=null) return
        val token=++generation
        fun update(action: () -> Unit) { scope.launch { if(token==generation) action() } }
        try {
            client=factory.invoke()
            timeout=scope.launch { delay(8_000); if(token==generation && !state.value.connected) {
                stop(); mutable.value=state.value.copy(message="Pleos 내비게이션 응답 시간 초과 · 다시 연결해 주세요")
            } }
            client!!.destinationListener { value -> update {
                if(!state.value.connected) return@update
                if(value == null || value.seconds !in 0..604800 || value.meters < 0) clearDestination()
                else mutable.value=state.value.copy(destination=value.name.take(180),remainingSeconds=value.seconds,
                    remainingMeters=value.meters,readAtMs=clock())
            } }
            client!!.initialize(
                connected={update {
                    timeout?.cancel();mutable.value=PleosNavigationState(true,true,"Pleos 내비게이션 연결됨")
                    poll?.cancel()
                    poll=scope.launch { while(isActive && token==generation) {
                        if(state.value.readAtMs?.let { clock()-it !in 0..10_000 } == true) clearDestination()
                        try { client?.requestDestination() } catch (_: Exception) { clearDestination() }
                        delay(2_000)
                    } }
                }},
                failed={update {stop();mutable.value=state.value.copy(message="Pleos 내비게이션 연결 실패 · 서비스/권한 확인")}},
                error={code->update {clearDestination();mutable.value=state.value.copy(message="내비게이션 요청 실패: $code")}},
            )
        } catch (_: Exception) { stop();mutable.value=state.value.copy(message="Pleos 내비게이션 초기화 실패") }
    }
    fun open(screen: VehicleNavigationScreen, keyword: String = "", parked: Boolean) {
        if(!parked) {mutable.value=state.value.copy(message="내비게이션 설정은 P·정지 확인 후 가능합니다");return}
        if(!state.value.connected || client==null) {mutable.value=state.value.copy(message="Pleos 내비게이션 연결을 먼저 확인해 주세요");return}
        try {
            mutable.value=state.value.copy(message="차량 내비게이션에 화면 전환 요청 · 결과는 기본 내비게이션에서 확인하세요")
            client!!.open(screen,keyword.trim().take(180))
        } catch (_: Exception) {mutable.value=state.value.copy(message="내비게이션 화면 요청 실패 · 권한/지원 확인")}
    }
    fun stop() {
        ++generation;timeout?.cancel();timeout=null;poll?.cancel();poll=null;clearDestination()
        try {client?.release()} catch (_: Exception) { /* Service may already be gone. */ }
        client=null
        mutable.value=state.value.copy(connected=false,message=if(factory==null) "NaviHelper SDK 미포함" else "Pleos 내비게이션 연결 중지")
    }
}
