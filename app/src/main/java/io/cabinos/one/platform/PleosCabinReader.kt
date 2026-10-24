package io.cabinos.one.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface PleosCabinClient {
    fun initialize()
    suspend fun passenger():Boolean?
    fun invalidate() {}
    fun release()
}
data class CabinAdvice(val passengerDetected:Boolean?=null)

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
                while(isActive) {
                    val passenger=try { withTimeout(3000) { client.passenger() } }
                    catch (_:TimeoutCancellationException) { client.invalidate();null }
                    catch(e:CancellationException) { throw e }
                    catch (_:Exception) { client.invalidate();null }
                    mutable.value=CabinAdvice(passenger);delay(1000)
                }
            } catch(e:CancellationException) { throw e }
            catch (_:Exception) { mutable.value=CabinAdvice() }
            finally { try { client?.release() } catch (_:Exception) { } }
        }
    }
    fun stop() { job?.cancel();job=null;mutable.value=CabinAdvice() }
}
