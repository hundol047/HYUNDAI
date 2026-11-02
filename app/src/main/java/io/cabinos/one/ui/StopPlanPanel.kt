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
            Text("출발 시간을 설정하면 집중·휴식 시간을 자동으로 맞춥니다.", color = Muted)
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
            if(schedule.availableSeconds<60 && (s.selectedActivityId!=null || schedule.returnSeconds>0)) {
                Text("외출 계획의 복귀 여유 시간을 유지 중입니다.",color=Muted)
                ActionButton("차 안에서 보내기로 변경",onStay,enabled=s.parkedAllowed)
            }
            when {
                schedule.availableSeconds < 60 -> Text("새 활동보다 출발 준비를 시작해 주세요.", color = Amber)
                session != null -> {
                    Text(
                        "${session.kind.label} ${timeText(session.remainingSeconds)} 남음 · ${if (session.status == SessionStatus.PAUSED) "일시정지" else "진행 중"}",
                        color = White,
                    )
                    val extra=minOf(300L,schedule.availableSeconds-session.remainingSeconds,7200L-session.durationSeconds)
                    val extension=if(extra%60==0L) "${extra/60}분" else "${extra/60}분 ${extra%60}초"
                    ActionButton(
                        if(extra>=60) "여유 안에서 $extension 더" else "연장할 시간 부족",
                        onExtend,
                        enabled =
                            s.parkedAllowed && extra>=60,
                    )
                    Text("1분 이상 여유가 있을 때만 연장하며 출발 시각은 유지합니다.", color = Muted, fontSize = 12.sp)
                }
                s.selectedActivityId != null || schedule.returnSeconds>0 -> {
                    Text("외출 계획의 복귀 여유 시간을 유지 중입니다. 현재 경로·출입 가능 여부를 확인해 주세요.", color = Muted)
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
