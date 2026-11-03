package io.cabinos.one.platform

import android.util.Log
import io.cabinos.core.AppState
import org.json.JSONObject

/** Opt-in ADB diagnostics. Disabled unless log.tag.CabinVehicle is explicitly enabled.
 * Records vehicle read/state evidence only, never location, destinations, or identifiers.
 */
internal class VehicleDiagnostics {
    private var last: List<Any?>? = null
    fun record(state: AppState, pleos: PleosReadState, energy: PleosEnergyState = PleosEnergyState(),
        navigation: PleosNavigationState = PleosNavigationState(false,message="")) {
        if (!Log.isLoggable("CabinVehicle",Log.INFO)) return
        val sample=state.sample ?: return
        val key=listOf(sample.source,sample.connected,sample.speedKph,sample.gear,state.parkedAllowed,
            pleos.included,pleos.brakePressed,pleos.successfulReadAtMs,energy.percent,energy.readAtMs,
            sample.batteryPercent,sample.charging,sample.chargeRemainingSeconds,energy.chargeSwitchOn,energy.chargeReadAtMs,
            state.schedule.departureAtMs,state.schedule.availableSeconds,state.chargeTargetPercent,state.session?.status,navigation.readAtMs,state.arrivalTargetMs)
        if(key==last)return
        last=key
        Log.i("CabinVehicle",JSONObject().apply {
            put("event","vehicle_read_state");put("atMs",state.nowMs)
            put("source",sample.source.name);put("connected",sample.connected)
            put("speedKph",sample.speedKph);put("gear",sample.gear.name)
            put("parkedAllowed",state.parkedAllowed);put("context",state.context.name)
            put("pleosSdkIncluded",pleos.included)
            put("brakePressed",pleos.brakePressed ?: JSONObject.NULL)
            put("sdkReadAtMs",pleos.successfulReadAtMs ?: JSONObject.NULL)
            put("batteryPercent",sample.batteryPercent);put("charging",sample.charging)
            put("chargeRemainingSeconds",sample.chargeRemainingSeconds ?: JSONObject.NULL)
            put("sdkBatteryPercent",energy.percent ?: JSONObject.NULL)
            put("sdkBatteryReadAtMs",energy.readAtMs ?: JSONObject.NULL)
            put("sdkChargeSwitch",energy.chargeSwitchOn ?: JSONObject.NULL)
            put("sdkChargeReadAtMs",energy.chargeReadAtMs ?: JSONObject.NULL)
            put("sdkSocUsed",sample.pleosSoc)
            put("departureAtMs",state.schedule.departureAtMs ?: JSONObject.NULL)
            put("availableSeconds",state.schedule.availableSeconds)
            put("chargeTargetPercent",state.chargeTargetPercent ?: JSONObject.NULL)
            put("planSource",state.schedule.estimateLabel)
            put("arrivalTargetMs",state.arrivalTargetMs ?: JSONObject.NULL)
            put("navigationReadAtMs",navigation.readAtMs ?: JSONObject.NULL)
            put("sessionKind",state.session?.kind?.name ?: JSONObject.NULL)
            put("sessionDurationSeconds",state.session?.durationSeconds ?: JSONObject.NULL)
            put("sessionStatus",state.session?.status?.name ?: JSONObject.NULL)
        }.toString())
    }
}
