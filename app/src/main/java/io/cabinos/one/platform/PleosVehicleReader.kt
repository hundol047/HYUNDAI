package io.cabinos.one.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A successful initialize() is not evidence of a connected or capable service. */
internal interface PleosBrakeClient {
    fun initialize()
    fun canRead(onComplete: (Boolean) -> Unit, onFailed: (Exception) -> Unit)
    fun read(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)
    fun release()
}

data class PleosReadState(
    val included: Boolean,
    val message: String,
    val brakePressed: Boolean? = null,
    val successfulReadAtMs: Long? = null,
)

/** Official SDK brake status is diagnostic only: pressing a pedal never proves P gear. */
class PleosVehicleReader internal constructor(
    private val factory: (() -> PleosBrakeClient)?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(PleosReadState(factory != null,
        if(factory != null) "Pleos Vehicle SDK 2.3.2 · 연결 대기" else "공식 Pleos SDK 미포함 · AAOS 속도·기어만 사용"))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    fun start() {
        if (factory == null || job?.isActive == true) return
        val token = ++generation
        job = scope.launch {
            var client: PleosBrakeClient? = null
            try {
                client = factory.invoke()
                client.initialize()
                while (isActive && token == generation) {
                    try {
                        val (supported, pressed) = withTimeout(3_000) {
                            val supported = awaitCallback<Boolean> { done, failed -> client.canRead(done, failed) }
                            supported to if(supported) awaitCallback<Boolean?> { done, failed -> client.read(done, failed) } else null
                        }
                        if (!supported) {
                            mutable.value = PleosReadState(true,"Pleos 브레이크 읽기 미지원")
                        } else {
                            mutable.value = if (pressed == null) PleosReadState(true,"Pleos 브레이크 값 없음")
                                else PleosReadState(true,"Pleos Vehicle SDK 브레이크 수신",pressed,clock())
                        }
                    } catch (_: TimeoutCancellationException) { mutable.value = PleosReadState(true,"Pleos SDK 응답 시간 초과") }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { mutable.value = PleosReadState(true,"Pleos SDK 읽기 실패 · 서비스/권한 확인") }
                    delay(1_000)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = PleosReadState(true,"Pleos SDK 초기화 실패") }
            finally { try {client?.release()} catch (_: Exception) { /* Disconnected service. */ } }
        }
    }
    fun stop() {
        ++generation; job?.cancel(); job = null
        if(factory != null) mutable.value = PleosReadState(true,"Pleos SDK 구독 중지 · 복귀 후 다시 확인")
    }
    private suspend fun <T> awaitCallback(register: ((T) -> Unit, (Exception) -> Unit) -> Unit): T =
        suspendCancellableCoroutine { continuation ->
            val once = java.util.concurrent.atomic.AtomicBoolean(false)
            register({ value ->
                if(once.compareAndSet(false,true) && continuation.isActive) continuation.resumeWith(Result.success(value))
            }, { error ->
                if(once.compareAndSet(false,true) && continuation.isActive) continuation.resumeWith(Result.failure(error))
            })
        }
}
