package io.cabinos.one.platform

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/** AAOS public API recommended by Pleos for standard speed and gear properties.
 * Binder reads and subscriptions run off the UI thread. No vehicle write API is used.
 * A new instance is created per foreground connection; never reused after stop().
 */
internal class AndroidCarPropertyTransport(private val context: Context) : VehiclePropertyTransport {
    private val thread = HandlerThread("CabinVehicleState")
    private lateinit var handler: Handler
    @Volatile private var stopped = false
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private lateinit var listener: VehiclePropertyTransport.Listener
    private val properties = intArrayOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.GEAR_SELECTION)
    private var connection = 0L
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null
    private fun onWorker(action: () -> Unit) {
        if(Looper.myLooper()==handler.looper) action() else handler.post { action() }
    }
    private fun callbackFor(token: Long) = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) { onWorker {
            if (!stopped && token == connection) deliver(value)
        } }
        override fun onErrorEvent(propertyId: Int, areaId: Int) { onWorker {
            if (!stopped && token == connection && propertyId in properties) listener.unavailable("차량 속성 오류: $propertyId")
        } }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (stopped || manager == null) return
            try {
                properties.forEach { id ->
                    val value = manager!!.getProperty<Any>(id, 0)
                    if (value == null) listener.unavailable("차량 속성 응답 없음: $id") else deliver(value)
                }
            } catch (_: SecurityException) { listener.unavailable("CAR_SPEED / CAR_POWERTRAIN 읽기 권한 없음") }
            catch (_: RuntimeException) { listener.unavailable("차량 속성 읽기 실패") }
            handler.postDelayed(this, 500)
        }
    }
    override fun start(listener: VehiclePropertyTransport.Listener) {
        this.listener = listener
        thread.start(); handler = Handler(thread.looper)
        handler.post {
            if (stopped) return@post
            try {
                car = Car.createCar(context, handler, 0L) { connectedCar, ready ->
                    handler.post {
                        if (stopped) { connectedCar.disconnect(); return@post }
                        detach()
                        if (!ready) { listener.disconnected("Car Service 연결 끊김"); return@post }
                        try {
                            val service = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                                ?: error("CarPropertyManager unavailable")
                            manager = service
                            if (properties.any { id -> service.getCarPropertyConfig(id)?.access?.let { it == 1 || it == 3 } != true }) {
                                listener.disconnected("속도 또는 기어 읽기 미지원 · 권한 확인 필요")
                                detach(); return@post
                            }
                            listener.connected()
                            val currentCallback = callbackFor(connection).also { callback = it }
                            properties.forEach { id ->
                                if (!service.registerCallback(currentCallback, id, CarPropertyManager.SENSOR_RATE_NORMAL)) {
                                    error("Vehicle subscription rejected")
                                }
                            }
                            handler.post(poll)
                        } catch (_: SecurityException) { detach(); listener.disconnected("CAR_SPEED / CAR_POWERTRAIN 읽기 권한 없음") }
                        catch (_: RuntimeException) { detach(); listener.disconnected("차량 속성 구독 실패") }
                    }
                }
                if (car == null) listener.disconnected("Car Service 사용 불가")
            } catch (_: SecurityException) { listener.disconnected("차량 서비스 권한 없음") }
            catch (_: RuntimeException) { listener.disconnected("차량 서비스 연결 실패") }
        }
    }
    private fun deliver(value: CarPropertyValue<*>) {
        if (value.propertyId !in properties || value.areaId != 0) return
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
            listener.unavailable("차량 속성 사용 불가: ${value.propertyId}"); return
        }
        when (value.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { listener.speed(it, value.timestamp) }
                ?: listener.unavailable("속도 데이터 형식 오류")
            VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let { listener.gear(it, value.timestamp) }
                ?: listener.unavailable("기어 데이터 형식 오류")
        }
    }
    private fun detach() {
        ++connection
        handler.removeCallbacks(poll)
        try { callback?.let { manager?.unregisterCallback(it) } } catch (_: RuntimeException) { /* Already disconnected. */ }
        callback = null; manager = null
    }
    override fun stop() {
        stopped = true
        if (!::handler.isInitialized) return
        handler.post {
            detach()
            try { car?.disconnect() } catch (_: RuntimeException) { /* Already disconnected. */ }
            car = null; thread.quitSafely()
        }
    }
}
