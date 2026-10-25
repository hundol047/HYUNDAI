@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cabinos.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One shared plan for phone and vehicle windows; actions still recheck the vehicle gate. */
@Composable
fun StopPlanPanel(
    s: AppState,
    onWaiting: (Int) -> Unit,
    onStart: (SessionKind, Int) -> Unit,
    onExtend: () -> Unit,
    onStay: () -> Unit,
) {
    val schedule = s.schedule
    val session =
        s.session?.takeIf {
            it.status == SessionStatus.RUNNING || it.status == SessionStatus.PAUSED
        }
    val departure = schedule.departureAtMs
    SurfacePanel(Modifier.fillMaxWidth(), accent = Mint) {
        Text("나의 정차 플랜", color = White, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        if (departure == null) {
            Text("언제 출발할까요? 시간을 정하면 집중·휴식 시간을 맞춰드려요.", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    ActionButton(
                        "${minutes}분 뒤 출발",
                        { onWaiting(minutes) },
                        enabled = s.parkedAllowed,
                    )
                }
            }
        } else {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            val finish = departure - (schedule.returnSeconds + schedule.bufferSeconds) * 1000
            Text(
                "활동 마감 ${format.format(Date(finish))} · 출발 ${format.format(Date(departure))}",
                color = Mint,
            )
            Text("${schedule.estimateLabel} 기준 · 변경 시 자동 조정", color = Muted, fontSize = 12.sp)
            Text(
                "활동 ${schedule.availableSeconds / 60}분 · 왕복 이동 ${schedule.returnSeconds / 60}분 · 출발 준비 ${schedule.bufferSeconds / 60}분",
                color = White,
            )
            Spacer(Modifier.height(8.dp))
            when {
                schedule.availableSeconds < 60 -> Text("새 활동보다 출발 준비를 시작해 주세요.", color = Amber)
                session != null -> {
                    Text(
                        "${session.kind.label} ${timeText(session.remainingSeconds)} 남음 · ${if (session.status == SessionStatus.PAUSED) "일시정지" else "진행 중"}",
                        color = White,
                    )
                    ActionButton(
                        "여유 안에서 5분 더",
                        onExtend,
                        enabled =
                            s.parkedAllowed &&
                                schedule.availableSeconds > session.remainingSeconds &&
                                session.durationSeconds < 7200,
                    )
                    Text("남은 여유만큼만 연장하며 출발 시각은 바꾸지 않아요.", color = Muted, fontSize = 12.sp)
                }
                s.selectedActivityId != null -> {
                    Text("선택한 장소의 왕복 시간을 확보했어요. 이동 시간은 도보 추정입니다.", color = Muted)
                    ActionButton("차 안에서 보내기로 변경", onStay, enabled = s.parkedAllowed)
                }
                else -> {
                    val minutes = (schedule.availableSeconds / 60).toInt()
                    val kind = if (minutes >= 15) SessionKind.FOCUS else SessionKind.RELAX
                    val preferred =
                        if (kind == SessionKind.FOCUS) s.preferences.focusMinutes
                        else s.preferences.relaxMinutes
                    val duration = minOf(minutes, preferred)
                    ActionButton(
                        "${duration}분 ${kind.label} 바로 시작",
                        { onStart(kind, duration) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = s.parkedAllowed,
                    )
                    Text("이동 없이 차 안에서 · 준비 시간을 제외한 추천", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
