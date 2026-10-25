package io.cabinos.one.platform

import android.content.Context
import io.cabinos.core.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.*
import org.json.JSONObject

data class RoadSafetyUi(
    val points: List<RoadSafetyPoint> = emptyList(),
    val approach: RoadSafetyApproach? = null,
    val notice: String = "충남 방지턱 참고자료 · 카메라·전국 자료 미연결",
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
        return RoadSafetyUi(nearby, index.approach(guidance, nearby, clock()), notice)
    }

    companion object {
        fun parse(text: String): List<RoadSafetyPoint> {
            val json = JSONObject(text)
            require(json.getInt("schema") == 1)
            val rows = json.getJSONArray("records")
            require(rows.length() <= 200_000)
            return (0 until rows.length()).map { i ->
                val row = rows.getJSONArray(i)
                val point = NavPoint(row.getDouble(1), row.getDouble(2))
                require(
                    point.valid() && point.latitude in 32.0..40.0 && point.longitude in 124.0..132.0
                )
                val kind =
                    when (row.getString(3)) {
                        "bump" -> RoadSafetyKind.BUMP
                        "camera" -> RoadSafetyKind.CAMERA
                        else -> error("Unknown road safety type")
                    }
                val date = LocalDate.parse(row.getString(5))
                val expires =
                    date.plusDays(365).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                RoadSafetyPoint(
                    row.getString(0),
                    point,
                    kind,
                    row.getString(4),
                    date.toString(),
                    expires,
                    if (row.isNull(6)) null else row.getInt(6).takeIf { it in 10..140 },
                    date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                )
            }
        }

        data class Dataset(val points: List<RoadSafetyPoint>, val notice: String)

        fun load(context: Context): List<RoadSafetyPoint> = loadDataset(context).points

        private fun loadDataset(context: Context): Dataset {
            val documents =
                context.assets
                    .list("road_safety")
                    .orEmpty()
                    .filter { it.endsWith(".json") }
                    .map { name ->
                        context.assets.open("road_safety/$name").use { stream ->
                            val out = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = stream.read(buffer)
                                if (n < 0) break
                                require(out.size() + n <= 24_000_000)
                                out.write(buffer, 0, n)
                            }
                            out.toString("UTF-8")
                        }
                    }
            val points = documents.flatMap(::parse)
            require(points.map { it.id }.distinct().size == points.size)
            require(points.map { it.kind to it.point }.distinct().size == points.size)
            val scope =
                documents.joinToString(" / ") {
                    val data = JSONObject(it)
                    "${data.getString("coverage")} · 자료 ${data.getString("asOf") }"
                }
            val missing =
                if (points.none { it.kind == RoadSafetyKind.CAMERA }) " · 카메라 미연결"
                else " · 단속 방향 미검증"
            return Dataset(points, "$scope · ${points.size}곳$missing")
        }
    }
}
