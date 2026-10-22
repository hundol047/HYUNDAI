package io.cabinos.one.ui

import io.cabinos.core.*
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import java.util.WeakHashMap

private data class MapStyle(val night:Boolean,val panel:Int,val line:Int,val background:Int)
private val appliedMapStyles=WeakHashMap<MapView,MapStyle>()

fun applyMapAppearance(view:MapView,settings:AppearanceSettings,palette:CabinPalette) {
    val style=MapStyle(settings.nightMap,palette.panel,palette.line,palette.background)
    if(appliedMapStyles[view]==style)return
    appliedMapStyles[view]=style
    view.overlayManager.tilesOverlay.apply {
        setColorFilter(if(settings.nightMap)TilesOverlay.INVERT_COLORS else null)
        loadingBackgroundColor=palette.panel or (0xFF shl 24)
        loadingLineColor=palette.line or (0xFF shl 24)
    }
    view.setBackgroundColor(palette.background or (0xFF shl 24))
}
