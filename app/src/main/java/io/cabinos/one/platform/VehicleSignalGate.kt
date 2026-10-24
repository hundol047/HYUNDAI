package io.cabinos.one.platform

import io.cabinos.core.*
import kotlin.math.abs

/** Read-only AAOS signal validation. All calls belong to one dispatcher.
 * Speed is CONTINUOUS; gear is ON_CHANGE and is re-read to confirm its current value.
 * Vehicle timestamps are elapsed-realtime nanoseconds, never wall-clock milliseconds.
 */
internal class VehicleSignalGate(
    private val wallMs: () -> Long,
    private val elapsedNs: () -> Long,
) {
    private var speed: Double? = null
    private var gear: Gear? = null
    private var speedNs = 0L
    private var gearNs = 0L
    private var gearReadNs = 0L
    private var ready = false
    var problem: String = "차량 서비스 연결 대기"
        private set

    fun reset(reason: String) {
        ready = false; speed = null; gear = null
        speedNs = 0; gearNs = 0; gearReadNs = 0; problem = reason
    }
    fun connected() { reset("속도·기어 첫 수신 대기"); ready = true }
    fun unavailable(reason: String) {
        speed = null; gear = null; gearReadNs = 0; problem = reason
    }
    fun speed(value: Float, timestampNs: Long) {
        if (!ready) return
        if (!value.isFinite() || abs(value) > 200f || !validTime(timestampNs) || timestampNs < speedNs) {
            unavailable("속도 신호 오류 또는 역순 수신"); return
        }
        speed = abs(value.toDouble()) * 3.6; speedNs = timestampNs
    }
    fun gear(value: Int, timestampNs: Long) {
        if (!ready) return
        if (!validTime(timestampNs) || timestampNs < gearNs) {
            unavailable("기어 신호 시간 오류"); return
        }
        // android.car.VehicleGear constants; exact enums, never a bitmask/P assumption.
        gear = when (value) {
            4 -> Gear.PARK
            8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096 -> Gear.DRIVE
            2 -> Gear.REVERSE
            1 -> Gear.NEUTRAL
            else -> Gear.UNKNOWN
        }
        gearNs = timestampNs; gearReadNs = elapsedNs()
    }
    private fun validTime(value: Long) = value > 0 && value <= elapsedNs()
    fun sample(): VehicleSample {
        val now = elapsedNs()
        val valid = ready && speed != null && gear != null && gear != Gear.UNKNOWN &&
            now - speedNs in 0..3_000_000_000L && now - gearReadNs in 0..3_000_000_000L
        if (valid) problem = "AAOS 속도·기어 수신 중"
        else if (ready && speed != null && gear != null) problem = "차량 신호 만료 또는 알 수 없는 기어"
        return VehicleSample(
            speedKph = if (valid) speed!! else 0.0,
            gear = if (valid) gear!! else Gear.UNKNOWN,
            parkingReliable = valid, connected = valid,
            charging = false, batteryPercent = -1, chargeRemainingSeconds = null,
            timestampMs = wallMs(), source = DataSource.VEHICLE,
        )
    }
}
