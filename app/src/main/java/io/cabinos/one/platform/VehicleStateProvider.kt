package io.cabinos.one.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import io.cabinos.core.VehicleDataProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface VehiclePropertyTransport {
    interface Listener {
        fun connected()
        fun speed(value: Float, timestampNs: Long)
        fun gear(value: Int, timestampNs: Long)
        fun receiptPoll(speed:Float,gear:Int,receivedNs:Long,subscribed:Boolean,connected:Boolean) {}
        fun timing(speedCallbackNs: Long?, pollSuccessNs: Long?) {}
        fun energy(property: EnergyProperty, value: Number?, timestampNs: Long) {}
        fun unavailable(reason: String)
        fun disconnected(reason: String)
    }
    fun start(listener: Listener)
    fun stop()
}

/** Real vehicle state only. Simulation never enters this provider. */
class VehicleStateProvider internal constructor(
    val automotive: Boolean,
    private val transportFactory: () -> VehiclePropertyTransport,
    private val scope: CoroutineScope,
    wallMs: () -> Long = System::currentTimeMillis,
    private val elapsedNs: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val debugLog: (String) -> Unit = {},
) : VehicleDataProvider {
    constructor(context: Context) : this(
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
        { AndroidCarPropertyTransport(context.applicationContext) },
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        debugLog = { android.util.Log.d("CabinVehicle",it) },
    )
    private val gate = VehicleSignalGate(wallMs, elapsedNs, io.cabinos.one.BuildConfig.SPEED_RECEIPT_FRESHNESS)
    private val mutable = MutableStateFlow(gate.sample())
    override val samples = mutable.asStateFlow()
    private val mutableStatus = MutableStateFlow(gate.problem)
    val status = mutableStatus.asStateFlow()
    private val mutableDiagnostics = MutableStateFlow(VehicleSignalDiagnostics())
    val diagnostics = mutableDiagnostics.asStateFlow()
    private var callbackNs: Long? = null
    private var pollNs: Long? = null
    private var lastDebugNs: Long? = null
    private fun clearTiming() { callbackNs=null;pollNs=null }
    private var epoch = 0L
    private var transport: VehiclePropertyTransport? = null
    private var monitor: Job? = null
    private var active = false

    fun start() {
        if (active) return
        active = true
        val current = ++epoch
        clearTiming()
        gate.reset(if (automotive) "차량 서비스 연결 중" else "차량 서비스 없음 · AAOS/Pleos 기기 필요")
        publish()
        if (!automotive) return // Do not load android.car classes on phones.
        fun accept(action: () -> Unit) { scope.launch {
            if (active && current == epoch) { action(); publish() }
        } }
        val listener = object : VehiclePropertyTransport.Listener {
            override fun connected() = accept { clearTiming();gate.connected() }
            override fun timing(speedCallbackNs:Long?,pollSuccessNs:Long?) = accept { callbackNs=speedCallbackNs;pollNs=pollSuccessNs }
            override fun receiptPoll(speed:Float,gear:Int,receivedNs:Long,subscribed:Boolean,connected:Boolean) = accept { gate.receiptPoll(speed,gear,receivedNs,subscribed,connected) }
            override fun speed(value: Float, timestampNs: Long) = accept { gate.speed(value, timestampNs) }
            override fun gear(value: Int, timestampNs: Long) = accept { gate.gear(value, timestampNs) }
            override fun energy(property: EnergyProperty, value: Number?, timestampNs: Long) = accept { gate.energy(property, value, timestampNs) }
            override fun unavailable(reason: String) = accept { gate.unavailable(reason) }
            override fun disconnected(reason: String) = accept { clearTiming();gate.reset(reason) }
        }
        try {
            transport = transportFactory()
            transport!!.start(listener)
        } catch (_: LinkageError) { connectionFailed("차량 API 라이브러리 없음") }
        catch (_: SecurityException) { connectionFailed("차량 읽기 권한 없음") }
        catch (_: RuntimeException) { connectionFailed("차량 서비스 연결 실패") }
        monitor = scope.launch { while (isActive) { delay(250); publish() } }
    }
    private fun publish() {
        val next=gate.sample()
        val previous=mutable.value
        // Keep the 250 ms watchdog without repainting an unchanged dashboard four times/sec.
        if(next.copy(timestampMs=previous.timestampMs)!=previous || next.timestampMs-previous.timestampMs !in 0..999) mutable.value=next
        mutableStatus.value=gate.problem
        val timing=gate.diagnostics().copy(lastSpeedCallbackMs=callbackNs?.div(1_000_000),lastPollSuccessMs=pollNs?.div(1_000_000))
        mutableDiagnostics.value=timing
        val now=elapsedNs()
        if (active && automotive && (lastDebugNs==null || now-lastDebugNs!!>=1_000_000_000)) {
            lastDebugNs=now
            debugLog("speedAgeMs=${timing.speedAgeMs} lastSpeedCallbackElapsedMs=${timing.lastSpeedCallbackMs} lastPollSuccessElapsedMs=${timing.lastPollSuccessMs} gearAgeMs=${timing.gearAgeMs} gearReadAgeMs=${timing.gearReadAgeMs} gear=${timing.rawGear} speedKph=${timing.rawSpeedKph}")
        }
    }
    fun stop() {
        active = false; ++epoch; monitor?.cancel(); monitor = null
        clearTiming()
        gate.reset("차량 신호 구독 중지 · 복귀 후 다시 확인"); publish()
        releaseTransport()
    }
    private fun connectionFailed(reason:String) {
        ++epoch // Reject callbacks from a partially initialized transport.
        clearTiming();gate.reset(reason);publish()
        releaseTransport()
    }
    private fun releaseTransport() {
        val previous=transport;transport=null
        try { previous?.stop() }
        catch (_:RuntimeException) { /* Safety state is already invalidated. */ }
        catch (_:LinkageError) { /* Optional car library may be unavailable. */ }
    }
    fun reconnect() { stop(); start() }
}
