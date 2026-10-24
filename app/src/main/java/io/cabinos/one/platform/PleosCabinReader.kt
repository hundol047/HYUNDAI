package io.cabinos.one.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface PleosCabinClient {
    fun initialize()
    suspend fun passenger():Boolean?
    suspend fun readiness():List<String>? = null
    fun invalidate() {}
    fun release()
}
data class CabinAdvice(val passengerDetected:Boolean?=null,val readiness:List<String>?=null)

/** Optional comfort advice never participates in the parking safety decision. */
class PleosCabinReader internal constructor(private val factory:(()->PleosCabinClient)?,
    private val scope:CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)) {
    private val mutable=MutableStateFlow(CabinAdvice())
    val state=mutable.asStateFlow()
    private var job:Job?=null
    fun start() {
        if(factory==null || job?.isActive==true)return
        job=scope.launch {
            var client:PleosCabinClient?=null
            try {
                client=factory();client.initialize()
                val active=client
                coroutineScope {
                    launch { poll(active,{active.passenger()}) { mutable.value=mutable.value.copy(passengerDetected=it) } }
                    launch { poll(active,{active.readiness()}) { mutable.value=mutable.value.copy(readiness=it) } }
                }
            } catch(e:CancellationException) { throw e }
            catch (_:Exception) { mutable.value=CabinAdvice() }
            finally { try { client?.release() } catch (_:Exception) { } }
        }
    }
    private suspend fun <T> poll(client:PleosCabinClient,read:suspend ()->T?,update:(T?)->Unit) {
        while(currentCoroutineContext().isActive) {
            val value=try { withTimeout(3000) { read() } }
            catch (_:TimeoutCancellationException) { client.invalidate();null }
            catch(e:CancellationException) { throw e }
            catch (_:Exception) { client.invalidate();null }
            update(value);delay(1000)
        }
    }
    fun stop() { job?.cancel();job=null;mutable.value=CabinAdvice() }
}
