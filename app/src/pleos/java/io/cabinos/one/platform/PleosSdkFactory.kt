package io.cabinos.one.platform

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType

/** Compiled only against the official ai.pleos.playground:Vehicle:2.3.2 AAR.
 * No SDK stubs, reflection substitutes, vehicle writes, or initialization-as-success.
 */
internal object PleosSdkFactory {
    fun create(context: Context) = PleosVehicleReader({ object : PleosBrakeClient {
        private val vehicle = Vehicle(context.applicationContext)
        override fun initialize() = vehicle.initialize()
        override fun canRead(onComplete: (Boolean) -> Unit, onFailed: (Exception) -> Unit) {
            vehicle.getBrake().checkBrakePedalStatusCapabilities(
                onComplete = { config -> onComplete(config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) },
                onFailed = onFailed,
            )
        }
        override fun read(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit) {
            vehicle.getBrake().getCurrentBrakePedalStatus(onComplete = onComplete, onFailed = onFailed)
        }
        override fun canReadChargeSwitch(onComplete: (Boolean) -> Unit, onFailed: (Exception) -> Unit) {
            vehicle.getEvBattery().checkEvChargeSwitchCapabilities(
                onComplete={ config -> onComplete(config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) },
                onFailed=onFailed)
        }
        override fun readChargeSwitch(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit) =
            vehicle.getEvBattery().getEvChargeSwitchState(onComplete=onComplete,onFailed=onFailed)
        override fun release() = vehicle.release()
        override fun canReadEnergy(onComplete: (Boolean) -> Unit, onFailed: (Exception) -> Unit) {
            vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(
                onComplete = { config -> onComplete(config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) },
                onFailed = onFailed,
            )
        }
        override fun readEnergy(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit) {
            vehicle.getEvBattery().getEvBatteryStateOfCharge(onComplete = onComplete, onFailed = onFailed)
        }
    } })
}
