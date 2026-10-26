package io.cabinos.one.platform

import io.cabinos.core.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Explicit parked queries only. Dedicated foot graph, never the automobile OSRM instance. */
class WalkingService(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var connection: HttpURLConnection? = null
    private var lastRequest = 0L
    private val requestGate = Mutex()

    fun cancel() {
        connection?.disconnect()
    }

    fun clear() {
        cancel()
    }

    suspend fun roundTrip(origin: MapPosition, place: NearbyActivity): WalkingPlan =
        withContext(Dispatchers.IO) {
            requestGate.withLock {
                val destination =
                    MapPosition(requireNotNull(place.latitude), requireNotNull(place.longitude))
                require(origin.valid() && destination.valid() && !place.simulated)
                delay((1200 - (clock() - lastRequest)).coerceAtLeast(0))
                currentCoroutineContext().ensureActive()
                lastRequest = clock()
                val coordinates =
                    "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude};${origin.longitude},${origin.latitude}"
                val url =
                    "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coordinates?overview=full&geometries=geojson&steps=false&radiuses=40;40;40"
                val c = URL(url).openConnection() as HttpURLConnection
                connection = c
                val deadline =
                    launch(Dispatchers.Default) {
                        try {
                            delay(15000)
                        } finally {
                            c.disconnect()
                        }
                    }
                try {
                    c.connectTimeout = 5000
                    c.readTimeout = 10000
                    c.instanceFollowRedirects = false
                    c.setRequestProperty(
                        "User-Agent",
                        "CabinOS_ONE/${io.cabinos.one.BuildConfig.VERSION_NAME} (FutureLabs; Android)",
                    )
                    c.setRequestProperty("Accept", "application/json")
                    if (c.responseCode != 200)
                        throw java.io.IOException("보행 경로 서버 응답 오류 (${c.responseCode})")
                    val bytes =
                        c.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                require(out.size() + n <= 1_000_000)
                                out.write(buffer, 0, n)
                            }
                            out.toByteArray()
                        }
                    require(bytes.size <= 1_000_000)
                    currentCoroutineContext().ensureActive()
                    val result = parse(String(bytes, Charsets.UTF_8), origin, destination, clock())
                    result
                } finally {
                    deadline.cancel()
                    c.disconnect()
                    if (connection === c) connection = null
                }
            }
        }

    companion object {
        fun parse(
            json: String,
            origin: MapPosition,
            destination: MapPosition,
            now: Long,
        ): WalkingPlan {
            val root = JSONObject(json)
            require(root.getString("code") == "Ok") { "보행 경로를 찾지 못했습니다." }
            val ways = root.getJSONArray("waypoints")
            require(ways.length() == 3)
            val access = DoubleArray(3)
            listOf(origin, destination, origin).forEachIndexed { i, target ->
                val w = ways.getJSONObject(i)
                val loc = w.getJSONArray("location")
                val snapped = MapPosition(loc.getDouble(1), loc.getDouble(0))
                access[i] = NearbyGeometry.distanceMeters(snapped, target)
                require(
                    w.getDouble("distance").let { it.isFinite() && it in 0.0..40.0 } &&
                        snapped.valid() &&
                        NearbyGeometry.distanceMeters(snapped, target) <= 40
                ) {
                    "보행 출입 지점이 너무 멉니다."
                }
            }
            val route = root.getJSONArray("routes").getJSONObject(0)
            val legs = route.getJSONArray("legs")
            require(legs.length() == 2)
            val geometry = route.getJSONObject("geometry")
            require(geometry.getString("type") == "LineString")
            val coords = geometry.getJSONArray("coordinates")
            require(coords.length() in 2..10000)
            val points =
                (0 until coords.length()).map { i ->
                    coords.getJSONArray(i).let { MapPosition(it.getDouble(1), it.getDouble(0)) }
                }
            require(
                points.all { it.valid() } &&
                    NearbyGeometry.distanceMeters(points.first(), origin) <= 40 &&
                    NearbyGeometry.distanceMeters(points.last(), origin) <= 40 &&
                    points.any { NearbyGeometry.distanceMeters(it, destination) <= 40 }
            )
            fun duration(i: Int): Long {
                val seconds = legs.getJSONObject(i).getDouble("duration")
                val meters = legs.getJSONObject(i).getDouble("distance")
                require(
                    seconds.isFinite() &&
                        seconds in 0.0..14400.0 &&
                        meters.isFinite() &&
                        meters in 0.0..15000.0
                )
                // Keep the slower of provider time and a 1.1 m/s walking estimate; round up per
                // leg.
                require(meters + 80 >= NearbyGeometry.distanceMeters(origin, destination))
                return ceil((maxOf(seconds, meters / 1.1) + (access[i] + access[i + 1]) / 1.1) / 60)
                    .toLong()
                    .coerceAtLeast(1) * 60
            }
            return WalkingPlan(
                    origin,
                    duration(0),
                    duration(1),
                    route.getDouble("distance") + access[0] + 2 * access[1] + access[2],
                    points,
                    now,
                )
                .also { require(it.usable(now, origin)) }
        }
    }
}
