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
open class EchoRoadClient {
    var endpoint = ""
        private set

    var coverageLimited = false
        private set
    private var token = ""
    @Volatile private var connection: HttpURLConnection? = null

    fun configure(endpoint: String, token: String) {
        val input = endpoint.trim()
        val credential = token.trim()
        if (input.isEmpty()) {
            this.endpoint = ""
            this.token = ""
            cancel()
            return
        }
        val address = if ("://" in input) input else "https://$input"
        val u = URL(address)
        require(
            u.protocol == "https" &&
                u.host.isNotBlank() &&
                u.userInfo == null &&
                u.query == null &&
                u.ref == null &&
                credential.length in 24..256 &&
                credential.all { it in '!'..'~' && it !in "\"{}" }
        )
        this.endpoint = if (u.path.trimEnd('/') == "/healthz") address.substringBefore("/healthz") else address.trimEnd('/')
        this.token = credential
    }

    fun cancel() {
        connection?.disconnect()
    }

    open suspend fun verify(): Boolean = parseStatus(request("/v1/status"))

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
        val started = android.os.SystemClock.elapsedRealtime()
        val json = request("/v1/risks?lat=${f.point.latitude}&lon=${f.point.longitude}")
        val parsed=parse(json, f.point, now + android.os.SystemClock.elapsedRealtime() - started)
        coverageLimited=JSONObject(json).optBoolean("accidentZonesTruncated",false)
        return parsed
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
                    throw EchoHttpException(c.responseCode)
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
        fun areaLabel(r:JSONObject,year:Int):String {
            if(!r.has("kind"))return "${year}년 사고 다발 자료"
            val start=r.getInt("periodStart");val end=r.getInt("periodEnd")
            require(start in 2010..year && end==year)
            val title=when(r.getString("kind")) {
                "municipal"->"사고 다발구역"
                "pedestrian"->"보행자 사고 다발구역"
                "child_pedestrian"->"보행어린이 사고 다발구역"
                "icy"->"결빙 사고 이력 구역"
                else->throw IllegalArgumentException("알 수 없는 통계 유형")
            }
            return (if(start==end)"${end}년" else "${start}~${end}년")+" "+title
        }
        fun parseArea(r:JSONObject):HistoricalArea? {
            if(!r.has("geometry")){require(!r.has("kind"));return null}
            val geometry=r.getJSONObject("geometry");val coordinates=geometry.getJSONArray("coordinates")
            val polys=when(geometry.getString("type")) {
                "Polygon"->listOf(coordinates)
                "MultiPolygon"->{require(coordinates.length() in 1..32);(0 until coordinates.length()).map {coordinates.getJSONArray(it)}}
                else->throw IllegalArgumentException("구역 경계 형식 오류")
            }
            var count=0
            val area=HistoricalArea(polys.map {poly ->
                require(poly.length() in 1..32)
                (0 until poly.length()).map {i ->
                    val ring=poly.getJSONArray(i);count+=ring.length();require(ring.length()>=4 && count<=2048)
                    (0 until ring.length()).map {j ->
                        val p=ring.getJSONArray(j);require(p.length()==2)
                        NavPoint(p.getDouble(1),p.getDouble(0)).also {
                            require(it.latitude in 33.0..39.0 && it.longitude in 124.0..132.0)
                            require(NavigationGeometry.distance(NavPoint(r.getDouble("lat"),r.getDouble("lon")),it)<=1510)
                        }
                    }
                }
            })
            require(area.valid());return area
        }
        fun parseStatus(json: String): Boolean {
            val result = JSONObject(json)
            require(result.getString("status") == "ok")
            return result.getBoolean("accidentDatasetConnected")
        }
        fun connectionError(error: Exception): String = when (error) {
            is EchoHttpException -> when (error.status) {
                401, 403 -> "접근키 인증 실패 · Railway에 등록한 키 부분만 입력하세요."
                404 -> "연결 확인 API가 없습니다. Railway 최신 배포와 서버 주소를 확인하세요."
                429 -> "요청이 많습니다. 잠시 후 다시 연결하세요."
                else -> "서버 응답 오류 (${error.status}) · 잠시 후 다시 연결하세요."
            }
            is java.net.SocketTimeoutException -> "서버 응답 시간이 초과됐습니다. 인터넷 연결을 확인하고 다시 시도하세요."
            else -> "연결 확인 실패 · 서버 주소·인터넷 연결을 확인하세요."
        }

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
                            areaLabel(r,year),
                            source,
                            area = parseArea(r),
                        )
                    }
            return parsed.distinctBy { it.id } to root.getBoolean("accidentDatasetConnected")
        }
    }
}

class EchoHttpException(val status: Int) : java.io.IOException("EchoRoad HTTP $status")
