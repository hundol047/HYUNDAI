package io.cabinos.one.platform

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import kotlinx.coroutines.suspendCancellableCoroutine

/** Signatures inspected in official Vehicle 2.3.2; read-only optional comfort signals. */
internal object PleosCabinFactory {
    fun create(context:Context)=PleosCabinReader({SdkCabinClient(context.applicationContext)})
}
private class SdkCabinClient(context:Context):PleosCabinClient {
    private val vehicle=Vehicle(context)
    private data class Cached(val value:Any?,val at:Long)
    private val cache=mutableMapOf<String,Cached>()
    override fun initialize()=vehicle.initialize()
    override fun release()=vehicle.release()
    override fun invalidate() { cache.clear() }
    private fun readable(access:AccessType)=access==AccessType.READ || access==AccessType.READ_WRITE
    private suspend fun <T> await(register:((T)->Unit,(Exception)->Unit)->Unit):T = suspendCancellableCoroutine { c ->
        val once=java.util.concurrent.atomic.AtomicBoolean()
        register({if(once.compareAndSet(false,true) && c.isActive)c.resumeWith(Result.success(it))},
            {if(once.compareAndSet(false,true) && c.isActive)c.resumeWith(Result.failure(it))})
    }
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> supported(key:String,check:((T?)->Unit,(Exception)->Unit)->Unit):T? {
        val now=android.os.SystemClock.elapsedRealtime()
        cache[key]?.let { if(it.value!=null || now-it.at in 0..29_999)return it.value as T? }
        val value=await(check);cache[key]=Cached(value,now);return value
    }
    // https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-occupancy-capabilities
    // https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-occupancy
    override suspend fun passenger():Boolean? {
        val seat=vehicle.getSeat()
        val areas=supported<List<SeatArea>>("seat") { done,failed ->
            seat.checkSeatOccupancyCapabilities(onComplete={config,values -> done(values.takeIf { readable(config.accessType) && it.isNotEmpty() })},onFailed=failed)
        } ?: return null
        // A single front seat cannot identify the driver on both left/right-hand-drive vehicles.
        val individual=areas.filter { it.values.size==1 }
        if(individual.isEmpty())return null
        var occupied=0
        for(area in individual) {
            val present=await<Boolean?> { done,failed -> seat.getCurrentSeatOccupancy(area,onComplete=done,onFailed=failed) }
                ?: run { cache.remove("seat");return null }
            if(present) {
                occupied++
                if(!area.values.single().name.startsWith("ROW_1_") || occupied>=2)return true
            }
        }
        return false
    }
}
