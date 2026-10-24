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
    elapsedNs: () -> Long = SystemClock::elapsedRealtimeNanos,
) : VehicleDataProvider {
    constructor(context: Context) : this(
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
        { AndroidCarPropertyTransport(context.applicationContext) },
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )
    private val gate = VehicleSignalGate(wallMs, elapsedNs)
    private val mutable = MutableStateFlow(gate.sample())
    override val samples = mutable.asStateFlow()
    private val mutableStatus = MutableStateFlow(gate.problem)
    val status = mutableStatus.asStateFlow()
    private var epoch = 0L
    private var transport: VehiclePropertyTransport? = null
    private var monitor: Job? = null
    private var active = false

    fun start() {
        if (active) return
        active = true
        val current = ++epoch
        gate.reset(if (automotive) "차량 서비스 연결 중" else "차량 서비스 없음 · AAOS/Pleos 기기 필요")
        publish()
        if (!automotive) return // Do not load android.car classes on phones.
        fun accept(action: () -> Unit) { scope.launch {
            if (active && current == epoch) { action(); publish() }
        } }
        val listener = object : VehiclePropertyTransport.Listener {
            override fun connected() = accept { gate.connected() }
            override fun speed(value: Float, timestampNs: Long) = accept { gate.speed(value, timestampNs) }
            override fun gear(value: Int, timestampNs: Long) = accept { gate.gear(value, timestampNs) }
            override fun unavailable(reason: String) = accept { gate.unavailable(reason) }
            override fun disconnected(reason: String) = accept { gate.reset(reason) }
        }
        try {
            transport = transportFactory().also { it.start(listener) }
        } catch (_: LinkageError) { gate.reset("차량 API 라이브러리 없음"); publish() }
        catch (_: SecurityException) { gate.reset("차량 읽기 권한 없음"); publish() }
        catch (_: RuntimeException) { gate.reset("차량 서비스 연결 실패"); publish() }
        monitor = scope.launch { while (isActive) { delay(250); publish() } }
    }
    private fun publish() {
        val next=gate.sample()
        val previous=mutable.value
        // Keep the 250 ms watchdog without repainting an unchanged dashboard four times/sec.
        if(next.copy(timestampMs=previous.timestampMs)!=previous || next.timestampMs-previous.timestampMs !in 0..999) mutable.value=next
        mutableStatus.value=gate.problem
    }
    fun stop() {
        active = false; ++epoch; monitor?.cancel(); monitor = null
        transport?.stop(); transport = null
        gate.reset("차량 신호 구독 중지 · 복귀 후 다시 확인"); publish()
    }
    fun reconnect() { stop(); start() }
}
