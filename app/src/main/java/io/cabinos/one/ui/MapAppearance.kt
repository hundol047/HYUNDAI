package io.cabinos.one.ui

import io.cabinos.core.*
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

fun applyMapAppearance(view:MapView,settings:AppearanceSettings,palette:CabinPalette) {
    view.overlayManager.tilesOverlay.apply {
        setColorFilter(if(settings.nightMap)TilesOverlay.INVERT_COLORS else null)
        loadingBackgroundColor=palette.panel or (0xFF shl 24)
        loadingLineColor=palette.line or (0xFF shl 24)
    }
    view.setBackgroundColor(palette.background or (0xFF shl 24))
}
