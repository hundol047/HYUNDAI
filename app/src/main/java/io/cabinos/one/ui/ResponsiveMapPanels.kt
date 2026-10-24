package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun ResponsiveMapPanels(
    modifier: Modifier, compact: Boolean,
    map: @Composable (Modifier) -> Unit, details: @Composable (Modifier) -> Unit,
) {
    if(compact) Column(modifier,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        map(Modifier.fillMaxWidth().height(300.dp))
        details(Modifier.fillMaxWidth().height(500.dp))
    } else Row(modifier,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        map(Modifier.weight(.6f).fillMaxHeight())
        details(Modifier.weight(.4f).fillMaxHeight())
    }
}
