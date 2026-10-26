package io.cabinos.one.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.cabinos.core.*
import io.cabinos.one.platform.NavigationUi
import java.util.Locale

/** Expanding changes only presentation, never navigation or parking state. */
@Composable
internal fun ExpandableNavigationMap(
    ui: NavigationUi,
    online: Boolean,
    interactive: Boolean,
    onRepeat: () -> Unit = {},
    onStop: () -> Unit = {},
    onPick: (NavPlace) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var wasActive by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.guidance.active) {
        if (ui.guidance.active && !wasActive) expanded = true
        wasActive = ui.guidance.active
    }
    Box(Modifier.fillMaxSize()) {
        if (!expanded)
            NavigationMap(
                ui.guidance,
                online,
                false,
                ui.selectedPlace,
                ui.mapFix,
                false,
                {},
                showPicker = false,
                traffic = ui.traffic,
                safetyPoints = ui.roadSafety.points,
            )
        Box(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "지도 크게 보기" }
                .clickable(role = Role.Button, onClickLabel = "지도 크게 보기") { expanded = true }
        )
        Text(
            "터치하여 지도 크게 보기",
            color = White,
            fontSize = 12.sp,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Panel.copy(alpha = .96f))
                    .padding(12.dp)
                    .clickable { expanded = true },
        )
    }
    if (expanded)
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(Modifier.fillMaxSize().background(Panel).testTag("expanded-navigation")) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("내비게이션", color = White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    ActionButton(
                        "지도 축소",
                        { expanded = false },
                        Modifier.heightIn(min = 48.dp),
                        Blue,
                    )
                }
                val g = ui.guidance
                val reliable = g.active && g.tracking && !g.offRoute && !g.directionUncertain
                if (g.active || g.arrived) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (reliable) Text(g.turnSymbol, color = Mint, fontSize = 48.sp)
                        Column(Modifier.weight(1f)) {
                            if (reliable)
                                Text(
                                    if (g.turnMeters >= 1000)
                                        String.format(Locale.US, "%.1f km 앞", g.turnMeters / 1000)
                                    else "${g.turnMeters.toInt().coerceAtLeast(0)} m 앞",
                                    color = Mint,
                                    fontSize = 28.sp,
                                )
                            Text(
                                if (reliable) g.nextInstruction else g.status,
                                color = if (reliable) White else Amber,
                                fontSize = 22.sp,
                                maxLines = 2,
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    NavigationMap(
                        g,
                        online,
                        true,
                        ui.selectedPlace,
                        ui.mapFix,
                        !ui.busy,
                        onPick,
                        showPicker = interactive,
                        showHealth = false,
                        traffic = ui.traffic,
                        safetyPoints = ui.roadSafety.points,
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val destination = g.route?.destination ?: ui.selectedPlace
                    if (destination != null) Text(destination.name, color = White, fontSize = 16.sp)
                    if (g.route != null)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "남은 거리 ${String.format(Locale.US,"%.1f",g.remainingMeters/1000)} km",
                                color = Mint,
                                fontSize = 18.sp,
                            )
                            val speed =
                                g.fix?.speedMps?.takeIf { reliable && it.isFinite() && it >= 0 }
                            Text(
                                if (speed != null) "${(speed*3.6).toInt()} km/h · GPS"
                                else "속도 확인 중",
                                color = White,
                                fontSize = 18.sp,
                            )
                        }
                    if (reliable)
                        Text(
                            "약 ${kotlin.math.ceil(g.remainingSeconds/60).toInt().coerceAtLeast(1)}분 남음 · 교통 미반영",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    ui.echoAhead?.let { Text(it,color=Amber,fontSize=16.sp) }
                    ui.roadSafety.approach?.let { approach ->
                        Text(
                            "경로 주변 ${approach.label()} · 약 ${approach.roundedMeters}m · 현장 표지 확인",
                            color = Amber,
                            fontSize = 16.sp,
                        )
                    }
                    if (g.active) {
                        Text(ui.roadSafety.notice, color = Amber, fontSize = 11.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton(
                                "다시 듣기",
                                onRepeat,
                                Modifier.weight(1f).heightIn(min = 52.dp),
                                Blue,
                                reliable,
                            )
                            ActionButton(
                                "안내 종료",
                                {
                                    onStop()
                                    expanded = false
                                },
                                Modifier.weight(1f).heightIn(min = 52.dp),
                                Muted,
                            )
                        }
                    }
                    Text(ui.gpsNotice, color = if (reliable) Muted else Amber, fontSize = 12.sp)
                    if (ui.notice.isNotBlank() && ui.notice != g.status)
                        Text(
                            ui.notice,
                            color = if (reliable) Muted else Amber,
                            fontSize = 12.sp,
                            maxLines = 2,
                        )
                }
            }
        }
}
