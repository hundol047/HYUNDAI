package io.cabinos.one.platform

import io.cabinos.core.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Access token and precise coordinates are memory-only. HTTPS only; never follow auth redirects.
 */
class EchoRoadClient {
    var endpoint = ""
        private set

    private var token = ""
    @Volatile private var connection: HttpURLConnection? = null

    fun configure(endpoint: String, token: String) {
        if (endpoint.isEmpty()) {
            this.endpoint = ""
            this.token = ""
            cancel()
            return
        }
        val u = URL(endpoint)
        require(
            u.protocol == "https" &&
                u.host.isNotBlank() &&
                u.userInfo == null &&
                u.query == null &&
                u.ref == null &&
                token.length in 24..256 &&
                token.all { it in '!'..'~' }
        )
        this.endpoint = endpoint.trimEnd('/')
        this.token = token
    }

    fun cancel() {
        connection?.disconnect()
    }

    suspend fun report(candidate: BrakeCandidate) {
        val b =
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("lat", candidate.point.latitude)
                .put("lon", candidate.point.longitude)
                .put("heading", candidate.heading)
                .put("observedAtMs", candidate.atMs)
                .put("deceleration", candidate.deceleration)
        request("/v1/observations", b.toString())
    }

    suspend fun read(f: NavFix, now: Long): Pair<List<EchoRisk>, Boolean> {
        val json = request("/v1/risks?lat=${f.point.latitude}&lon=${f.point.longitude}")
        return parse(json, f.point, now)
    }

    private suspend fun request(path: String, body: String? = null): String =
        withContext(Dispatchers.IO) {
            require(endpoint.isNotEmpty())
            val c = URL(endpoint + path).openConnection() as HttpURLConnection
            connection = c
            val timeout =
                launch(Dispatchers.Default) {
                    try {
                        delay(12000)
                    } finally {
                        c.disconnect()
                    }
                }
            try {
                c.connectTimeout = 4000
                c.readTimeout = 7000
                c.instanceFollowRedirects = false
                c.setRequestProperty("Authorization", "Bearer $token")
                c.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    val bytes = body.toByteArray()
                    c.setFixedLengthStreamingMode(bytes.size)
                    c.outputStream.use { it.write(bytes) }
                }
                if (c.responseCode !in 200..299)
                    throw java.io.IOException("EchoRoad 서버 응답 오류 (${c.responseCode})")
                c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        require(out.size() + n <= 200000)
                        out.write(buf, 0, n)
                    }
                    currentCoroutineContext().ensureActive()
                    out.toString("UTF-8")
                }
            } finally {
                timeout.cancel()
                c.disconnect()
                if (connection === c) connection = null
            }
        }

    companion object {
        fun parse(json: String, origin: NavPoint, now: Long): Pair<List<EchoRisk>, Boolean> {
            val root = JSONObject(json)
            require(now - root.getLong("generatedAtMs") in -2000..30000)
            val risks = root.getJSONArray("risks")
            val zones = root.getJSONArray("accidentZones")
            require(risks.length() <= 50 && zones.length() <= 50)
            fun point(r: JSONObject): NavPoint =
                NavPoint(r.getDouble("lat"), r.getDouble("lon")).also {
                    require(it.valid() && NavigationGeometry.distance(origin, it) <= 2200)
                }
            val parsed =
                (0 until risks.length()).map { i ->
                    val r = risks.getJSONObject(i)
                    require(
                        r.getString("kind") == "repeated_braking" &&
                            r.getInt("reporters") >= 2 &&
                            now - r.getLong("observedAtMs") in -2000..900000
                    )
                    val expiry = r.getLong("expiresAtMs")
                    val heading = r.getDouble("heading")
                    require(
                        expiry - now in 1..902000 && heading.isFinite() && heading in 0.0..359.999
                    )
                    EchoRisk(
                        r.getString("id").take(80),
                        point(r),
                        heading,
                        minOf(expiry, now + 90000),
                        "반복 급감속 제보",
                        "기기 제보 · 사고 확정 아님",
                        r.getInt("reporters"),
                    )
                } +
                    (0 until zones.length()).map { i ->
                        val r = zones.getJSONObject(i)
                        val expiry = r.getLong("validUntilMs")
                        val year = r.getInt("year")
                        val source = r.getString("source")
                        require(
                            expiry > now &&
                                year in 2020..2100 &&
                                source.startsWith("https://") &&
                                source.length <= 300
                        )
                        EchoRisk(
                            "zone-" + r.getString("id").take(80),
                            point(r),
                            null,
                            minOf(expiry, now + 90000),
                            "${year}년 사고 다발 자료",
                            source,
                        )
                    }
            return parsed.distinctBy { it.id } to root.getBoolean("accidentDatasetConnected")
        }
    }
}
