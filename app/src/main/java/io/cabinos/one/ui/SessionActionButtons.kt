package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cabinos.core.*

/** The same large controls and disabled-state explanation in both window layouts. */
@Composable
internal fun SessionActionButtons(s: AppState, onPauseOrResume: () -> Unit, onEnd: () -> Unit) {
    val session = s.session ?: return
    val running = session.status == SessionStatus.RUNNING
    val resumable =
        session.status == SessionStatus.PAUSED &&
            session.remainingSeconds > 0 &&
            s.schedule.availableSeconds > 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(
                if (running) "일시정지" else "계속",
                onPauseOrResume,
                Modifier.weight(1f).heightIn(min = 56.dp),
                enabled = s.parkedAllowed && (running || resumable),
            )
            ActionButton(
                "종료",
                onEnd,
                Modifier.weight(1f).heightIn(min = 56.dp),
                color = Muted,
                enabled = s.parkedAllowed,
            )
        }
        if (session.status == SessionStatus.PAUSED && !resumable) {
            Text("다시 시작할 여유가 없어요. 출발 준비 또는 시간 설정을 확인하세요.", color = Amber, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun AmbientQuickActions(
    preset: String?,
    enabled: Boolean,
    onChoose: () -> Unit,
    onStop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButton(
            if (preset == null) "배경 소리 선택" else "$preset · 소리 설정",
            onChoose,
            Modifier.weight(1f).heightIn(min = 56.dp),
            enabled = enabled,
        )
        if (preset != null) {
            ActionButton("소리 끄기", onStop, Modifier.heightIn(min = 56.dp), color = Muted)
        }
    }
}
