package io.cabinos.one.platform

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.*
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import kotlinx.coroutines.suspendCancellableCoroutine
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState

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
    // https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-door-position
    // https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-current-window-position
    // https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-temperature-current
    // https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-power-state
    override suspend fun readiness():List<String>? {
        val messages=mutableListOf<String>()
        val door=vehicle.getDoor()
        val doors=supported<Map<DoorArea,AreaBounds<Int>>>("door") { done,failed ->
            door.checkDoorPositionCapabilities(onComplete={config,areas -> done(areas.takeIf { readable(config.accessType) && it.isNotEmpty() })},onFailed=failed)
        }
        for((area,bounds) in doors.orEmpty()) {
            val position=await<Int?> { done,failed -> door.getCurrentDoorPosition(area,onComplete=done,onFailed=failed) }
            if(position==null)cache.remove("door")
            if(position!=null && 0 in bounds.minValue..bounds.maxValue && position in bounds.minValue..bounds.maxValue && position!=0) messages+="문이 열려 있어요"
        }
        val window=vehicle.getWindow()
        val windows=supported<Map<WindowArea,AreaBounds<Int>>>("window") { done,failed ->
            window.checkWindowPositionCapabilities(onComplete={config,areas -> done(areas.takeIf { readable(config.accessType) && it.isNotEmpty() })},onFailed=failed)
        }
        for((area,bounds) in windows.orEmpty()) {
            val position=await<Int?> { done,failed -> window.getCurrentWindowPosition(area,onComplete=done,onFailed=failed) }
            if(position==null)cache.remove("window")
            if(position!=null && 0 in bounds.minValue..bounds.maxValue && position in bounds.minValue..bounds.maxValue && position!=0) messages+="창문이 열려 있어요"
        }
        val hvac=vehicle.getHVAC()
        val temperatures=supported<List<SeatArea>>("temperature") { done,failed ->
            hvac.checkHvacTemperatureCurrentCapabilities(onComplete={config,areas -> done(areas.takeIf { readable(config.accessType) && it.isNotEmpty() })},onFailed=failed)
        }
        for(area in temperatures.orEmpty()) {
            val temperature=await<Float?> { done,failed -> hvac.getHvacTemperatureCurrent(area,TemperatureUnit.CELSIUS,onComplete=done,onFailed=failed) }
            if(temperature==null)cache.remove("temperature")
            if(temperature!=null && temperature.isFinite() && temperature in -50f..80f) messages+="실내 온도 ${temperature.toInt()}°C"
        }
        val powers=supported<List<SeatArea>>("power") { done,failed ->
            hvac.checkHvacPowerCapabilities(onComplete={config,areas -> done(areas.takeIf { readable(config.accessType) && it.isNotEmpty() })},onFailed=failed)
        }
        for(area in powers.orEmpty()) {
            val power=await<Boolean?> { done,failed -> hvac.getHvacPowerState(area,onComplete=done,onFailed=failed) }
            if(power==null)cache.remove("power") else messages+=if(power)"공조 켜짐" else "공조 꺼짐"
        }
        return messages.distinct().takeIf { it.isNotEmpty() }
    }
    // https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/has-low-washer-fluid-warning
    // https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-telltales-info
    override suspend fun care():List<String>? {
        val safety=vehicle.getSafety()
        val messages=mutableListOf<String>()
        val washer=supported<Boolean>("washer") { done,failed ->
            safety.checkLowWasherFluidWarningStateCapabilities(onComplete={config -> done(true.takeIf { readable(config.accessType) })},onFailed=failed)
        }
        if(washer==true) {
            val low=await<Boolean?> { done,failed -> safety.hasLowWasherFluidWarning(onComplete=done,onFailed=failed) }
            if(low==null)cache.remove("washer")
            if(low==true)messages+="워셔액 부족 경고를 확인하세요"
        }
        val telltales=supported<Boolean>("telltales") { done,failed ->
            safety.checkTelltalesCapabilities(onComplete={config -> done(true.takeIf { readable(config.accessType) })},onFailed=failed)
        }
        if(telltales==true) {
            val values=await<Telltales?> { done,failed -> safety.getTelltalesInfo(onComplete=done,onFailed=failed) }
            if(values==null)cache.remove("telltales")
            val active=values?.telltales.orEmpty().filter { it.state==TelltaleState.ON || it.state==TelltaleState.BLINK }
            if(active.isNotEmpty())messages+="계기판 표시등 ${active.size}개 켜짐 · 차량 안내를 확인하세요"
            if(active.any { it.type.name=="TPMS" })messages+="타이어 공기압 계통 표시등을 확인하세요"
        }
        // Tire.getLowTirePressure returns Float, not a Boolean warning flag. Do not invent one.
        return messages.takeIf { it.isNotEmpty() }
    }
}
