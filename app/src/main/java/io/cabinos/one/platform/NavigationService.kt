package io.cabinos.one.platform

import io.cabinos.core.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** Public OSM-based providers, explicit queries only. No country restriction or API key. */
open class NavigationService(private val clock:()->Long=System::currentTimeMillis) {
    var searchEndpoint = "https://nominatim.openstreetmap.org"
        private set

    var routeEndpoint = "https://router.project-osrm.org"
        private set

    @Volatile private var connection: HttpURLConnection? = null
    var language = "ko-KR"
    private var searchAccessToken = ""
    private var lastSearch = 0L
    private var lastRoute = 0L
    private val cache = linkedMapOf<String, Pair<Long, PlaceSearchPage>>()

    fun configure(search: String, route: String, accessToken: String = "") {
        fun valid(s: String): Boolean =
            runCatching {
                    val u = URL(s)
                    u.protocol == "https" &&
                        u.host.isNotBlank() &&
                        u.userInfo == null &&
                        u.query == null &&
                        u.ref == null
                }
                .getOrDefault(false)
        require(valid(search) && valid(route)) { "HTTPS 서비스 기본 주소를 입력해 주세요." }
        require(
            accessToken.isEmpty() ||
                (accessToken.length in 12..256 &&
                    accessToken.all { it in '!'..'~' } &&
                    URL(search).host.lowercase().trimEnd('.') != "nominatim.openstreetmap.org")
        ) {
            "접근키는 자체 검색 서버에만 사용하며 12~256자의 공백 없는 값이어야 합니다."
        }
        searchEndpoint = search.trimEnd('/')
        routeEndpoint = route.trimEnd('/')
        searchAccessToken = accessToken
        cache.clear()
    }

    fun clearCache() {
        cache.clear()
    }

    fun clearPersonalData() {
        clearCache()
        searchAccessToken = ""
    }

    fun cancel() {
        connection?.disconnect()
    }

    protected open suspend fun get(url: String, accessToken: String = ""): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            connection = c
            try {
                c.connectTimeout = 12_000
                c.readTimeout = 20_000
                c.instanceFollowRedirects = false
                c.setRequestProperty(
                    "User-Agent",
                    "CabinOS_ONE/${io.cabinos.one.BuildConfig.VERSION_NAME} (FutureLabs; Android)",
                )
                if (accessToken.isNotEmpty())
                    c.setRequestProperty("Authorization", "Bearer $accessToken")
                c.setRequestProperty("Accept", "application/json")
                c.setRequestProperty("Accept-Language", language + ",en;q=0.7")
                check(c.responseCode == 200) {
                    if (c.responseCode == 429) "서비스 요청 한도를 넘었습니다. 잠시 후 재시도하세요."
                    else if (
                        url.contains("/route/v1/driving/") &&
                            c.responseCode in listOf(400, 404, 422)
                    )
                        "경로 요청을 처리하지 못했습니다 (${c.responseCode}). 좌표·도로 연결·서버 주소를 확인하세요."
                    else "내비게이션 서버 연결 실패 (${c.responseCode})"
                }
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                c.inputStream.use { stream ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = stream.read(buffer)
                        if (n < 0) break
                        check(output.size() + n <= 12_000_000) { "경로가 너무 큽니다. 가까운 경유 구간으로 나누세요." }
                        output.write(buffer, 0, n)
                    }
                }
                output.toString("UTF-8")
            } finally {
                c.disconnect()
                if (connection === c) connection = null
            }
        }

    open suspend fun search(
        query: String,
        origin: NavPoint? = null,
        nearby: Boolean = false,
        excluded: List<String> = emptyList(),
    ): PlaceSearchPage {
        val requests = PlaceSearch.requests(query, origin, nearby, excluded, language)
        return searchPage(requests, origin, nearby, excluded)
    }

    private suspend fun searchPage(
        requests: List<Map<String, String>>,
        origin: NavPoint?,
        nearby: Boolean,
        excluded: List<String>,
    ): PlaceSearchPage {
        val key = language + "|" + requests.toString()
        cache[key]?.let {
            if (clock() - it.first in 0..59_999) return it.second
            else cache.remove(key)
        }
        val places = mutableListOf<NavPlace>()
        for (params in requests) {
            delay(requestWaitMillis(lastSearch,clock(),1500))
            currentCoroutineContext().ensureActive()
            lastSearch = clock()
            val encoded =
                params.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                }
            val raw = get("$searchEndpoint/search?$encoded", searchAccessToken)
            places += withContext(Dispatchers.Default) { parsePlaces(raw) }
        }
        val result = PlaceSearch.page(places, origin, nearby, excluded)
        if (cache.size >= 30) cache.remove(cache.keys.first())
        cache[key] = clock() to result
        return result
    }

    open suspend fun regionalSearch(query: String, origin: NavPoint?): RegionalSearchResult {
        for (split in RegionalPlaceSearch.splits(query)) {
            delay(requestWaitMillis(lastSearch,clock(),1500))
            currentCoroutineContext().ensureActive()
            lastSearch = clock()
            val params = PlaceSearch.requests(split.region, language = language).single()
            val encoded =
                params.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                }
            val raw = get("$searchEndpoint/search?$encoded", searchAccessToken)
            val candidates = parseRegions(raw, split.region)
            if (candidates.isEmpty()) continue
            val region =
                RegionalPlaceSearch.chooseRegion(candidates, origin)
                    ?: return RegionalSearchResult(emptyList())
            val radius = RegionalPlaceSearch.radius(region.category)
            val collected = mutableListOf<NavPlace>()
            for (name in RegionalPlaceSearch.names(split.name)) {
                val page =
                    searchPage(
                        PlaceSearch.requests(
                            name,
                            region.point,
                            true,
                            language = language,
                            radius = radius.toDouble(),
                        ),
                        region.point,
                        true,
                        emptyList(),
                    )
                val accepted =
                    page.places.filter {
                        RegionalPlaceSearch.acceptsCategory(split.name, it.category) &&
                            RegionalPlaceSearch.matchesName(split.name, it.name, region.address) &&
                            NavigationGeometry.distance(region.point, it.point) <= radius
                    }
                collected += accepted
            }
            return RegionalSearchResult(
                PlaceSearch.results(collected, region.point, true),
                region.name,
                split.name,
                radius,
            )
        }
        return RegionalSearchResult(emptyList())
    }

    open suspend fun route(origin: NavPoint, destination: NavPlace): DrivingRoute {
        require(origin.valid() && destination.point.valid())
        require(NavigationGeometry.distance(origin, destination.point) >= 20) {
            "목적지가 출발 위치와 너무 가깝습니다."
        }
        delay(requestWaitMillis(lastRoute,clock(),1500))
        currentCoroutineContext().ensureActive()
        lastRoute = clock()
        val p = destination.point
        val raw =
            get(
                "$routeEndpoint/route/v1/driving/${origin.longitude},${origin.latitude};${p.longitude},${p.latitude}?overview=full&geometries=geojson&steps=true&alternatives=false"
            )
        val route = withContext(Dispatchers.Default) { parseRoute(raw, destination) }
        NavigationGeometry.validateOrigin(route, origin)
        return route
    }

    companion object {
        fun parseRegions(raw: String, query: String): List<NavPlace> {
            val rows = JSONArray(raw)
            return (0 until minOf(6, rows.length())).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val details = row.optJSONObject("namedetails")
                val names =
                    listOf(row.optString("name")) +
                        details
                            ?.keys()
                            ?.asSequence()
                            ?.filter { RegionalPlaceSearch.aliasKey(it) }
                            ?.flatMap { details.optString(it).split(';').asSequence() }
                            ?.toList()
                            .orEmpty()
                if (!RegionalPlaceSearch.regionMatches(query, names, row.optString("type")))
                    return@mapNotNull null
                PlaceSearch.place(
                    row.optString("name"),
                    row.optDouble("lat", Double.NaN),
                    row.optDouble("lon", Double.NaN),
                    row.optString("place_id"),
                    row.optString("display_name"),
                    row.optString("type"),
                )
            }
        }

        fun parsePlaces(raw: String): List<NavPlace> {
            val array = JSONArray(raw)
            return (0 until minOf(6, array.length()))
                .mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    fun text(key: String) = if (o.isNull(key)) "" else o.optString(key)
                    val tags = o.optJSONObject("extratags") ?: JSONObject()
                    fun tag(key: String) = if (tags.isNull(key)) "" else tags.optString(key)
                    PlaceSearch.place(
                        text("name").ifBlank { text("display_name") },
                        text("lat").toDoubleOrNull() ?: Double.NaN,
                        text("lon").toDoubleOrNull() ?: Double.NaN,
                        text("place_id"),
                        text("display_name"),
                        PlaceSearch.category(text("type")),
                        tag("phone").ifBlank { tag("contact:phone") },
                        tag("website").ifBlank { tag("contact:website") },
                        tag("opening_hours"),
                        text("source").ifBlank { "OSM / Nominatim" },
                    )
                }
                .distinctBy { it.id.ifBlank { it.name + it.point } }
        }

        fun parseRoute(raw: String, destination: NavPlace): DrivingRoute {
            val json = JSONObject(raw)
            check(json.optString("code") == "Ok") { "자동차로 연결되는 경로를 찾지 못했습니다. 바다·국경·도로 정보를 확인하세요." }
            val route = json.getJSONArray("routes").getJSONObject(0)
            val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
            require(coordinates.length() <= 150_000)
            val points =
                (0 until coordinates.length()).map { i ->
                    val p = coordinates.getJSONArray(i)
                    NavPoint(p.getDouble(1), p.getDouble(0))
                }
            val legs = route.getJSONArray("legs")
            val maneuvers = mutableListOf<NavManeuver>()
            for (i in 0 until legs.length()) {
                val steps = legs.getJSONObject(i).getJSONArray("steps")
                for (j in 0 until steps.length()) {
                    require(maneuvers.size < 10_000)
                    val step = steps.getJSONObject(j)
                    val m = step.getJSONObject("maneuver")
                    val p = m.getJSONArray("location")
                    val road =
                        NavigationGeometry.roadLabel(
                            step.optString("name"),
                            step.optString("destinations"),
                            step.optString("ref"),
                        )
                    maneuvers.add(
                        NavManeuver(
                            NavPoint(p.getDouble(1), p.getDouble(0)),
                            m.getString("type"),
                            m.optString("modifier"),
                            road,
                            if (m.has("exit")) m.getInt("exit") else null,
                        )
                    )
                }
            }
            return NavigationGeometry.route(
                destination,
                points,
                route.getDouble("distance"),
                route.getDouble("duration"),
                maneuvers,
            )
        }
    }
}
