package io.cabinos.one.platform

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import io.cabinos.core.*
import java.io.Reader
import java.io.StringReader
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.*

data class RoadSafetyUi(
    val points: List<RoadSafetyPoint> = emptyList(),
    val approach: RoadSafetyApproach? = null,
    val upcoming: List<RoadSafetyApproach> = emptyList(),
    val notice: String = "안전시설 참고자료 · 위치 수신 후 확인",
)

/** App-bundled, read-only snapshots. No location uploads or network work here. */
internal class RoadSafetyRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private var loading = false
    private var index = RoadSafetyIndex(emptyList())
    private var notice = RoadSafetyUi().notice
    private var lastExpiry = Long.MAX_VALUE

    fun snapshot(guidance: Guidance, fix: NavFix?, foreground: Boolean): RoadSafetyUi {
        if (!foreground || fix == null) return RoadSafetyUi(notice = notice)
        if (!loading) {
            loading = true
            scope.launch {
                try {
                    val loaded =
                        withContext(Dispatchers.IO) {
                            val data = loadDataset(context)
                            Triple(
                                RoadSafetyIndex(data.points),
                                data.notice,
                                data.points.maxOfOrNull { it.expiresAtMs } ?: 0,
                            )
                        }
                    index = loaded.first
                    notice = loaded.second
                    lastExpiry = loaded.third
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    notice = "안전시설 자료를 읽지 못했습니다 · 경고 미제공"
                }
            }
        }
        if (clock() >= lastExpiry) return RoadSafetyUi(notice = "방지턱 자료 유효기간 경과 · 업데이트 전 경고 미제공")
        val nearby = index.nearby(fix, clock())
        val upcoming = index.upcoming(guidance, clock())
        return RoadSafetyUi(
            points = nearby,
            approach = upcoming.firstOrNull(),
            upcoming = upcoming,
            notice = notice,
        )
    }

    companion object {
        data class Dataset(val points: List<RoadSafetyPoint>, val notice: String)

        fun parse(text: String): List<RoadSafetyPoint> = readDataset(StringReader(text)).points

        private fun readDataset(input: Reader): Dataset =
            JsonReader(input).use { reader ->
                var schema = 0
                var coverage = ""
                var asOf = ""
                var oldest = ""
                var newest = ""
                val points = ArrayList<RoadSafetyPoint>()
                val dates = mutableMapOf<String, Pair<Long, Long>>()
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "schema" -> schema = reader.nextInt()
                        "coverage" -> coverage = reader.nextString()
                        "asOf" -> asOf = reader.nextString()
                        "recordDateRange" -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "oldest" -> oldest = reader.nextString()
                                    "newest" -> newest = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        }
                        "records" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                require(points.size < 200_000)
                                points += readPoint(reader, dates)
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                require(schema in 1..2 && reader.peek() == JsonToken.END_DOCUMENT)
                val dateLabel =
                    if (oldest.isNotBlank() && newest.isNotBlank()) "$oldest~$newest" else asOf
                Dataset(points, "$coverage · 기준일 $dateLabel · 1년 지난 자료 제외")
            }

        private fun readPoint(
            reader: JsonReader,
            dates: MutableMap<String, Pair<Long, Long>>,
        ): RoadSafetyPoint {
            reader.beginArray()
            val id = reader.nextString()
            val point = NavPoint(reader.nextDouble(), reader.nextDouble())
            require(
                point.valid() && point.latitude in 32.0..40.0 && point.longitude in 124.0..132.0
            )
            val kind =
                when (reader.nextString()) {
                    "bump" -> RoadSafetyKind.BUMP
                    "camera" -> RoadSafetyKind.CAMERA
                    else -> error("Unknown road safety type")
                }
            val road = reader.nextString()
            val observed = reader.nextString()
            val (from, expiry) =
                dates.getOrPut(observed) {
                    val date = LocalDate.parse(observed)
                    date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() to
                        date.plusDays(365).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                }
            val speed =
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    null
                } else reader.nextInt().takeIf { it in 10..140 }
            val advisory =
                if (reader.hasNext())
                    when (reader.nextString()) {
                        "fixed" -> CameraAdvisory.FIXED
                        "section" -> CameraAdvisory.SECTION
                        "section_start" -> CameraAdvisory.SECTION_START
                        "section_end" -> CameraAdvisory.SECTION_END
                        else -> CameraAdvisory.NONE
                    }
                else CameraAdvisory.NONE
            reader.endArray()
            require(id.length in 1..100 && road.length <= 120)
            return RoadSafetyPoint(id, point, kind, road, observed, expiry, speed, from, advisory)
        }

        fun load(context: Context): List<RoadSafetyPoint> = loadDataset(context).points

        private fun loadDataset(context: Context): Dataset {
            val datasets =
                context.assets
                    .list("road_safety")
                    .orEmpty()
                    .filter { it.endsWith(".json") }
                    .map { name ->
                        context.assets
                            .open("road_safety/$name")
                            .bufferedReader(Charsets.UTF_8)
                            .use { readDataset(it) }
                    }
            val points = datasets.flatMap { it.points }
            require(points.map { it.id }.distinct().size == points.size)
            require(points.map { it.kind to it.point }.distinct().size == points.size)
            val coverage = datasets.joinToString(" / ") { it.notice }
            val missing =
                if (points.none { it.kind == RoadSafetyKind.CAMERA }) " · 카메라 미연결"
                else " · 단속 방향 미검증"
            return Dataset(points, "$coverage · 수록 ${points.size}곳$missing")
        }
    }
}
