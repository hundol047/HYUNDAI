package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    @Test fun allInteriorPalettesKeepTextAndCustomAccentLegible() {
        for(p in AppearanceCatalog.presets)for(mode in listOf("system","comfort","night"))for(accent in listOf("","#000000","#FFFFFF","#FF0000","#001100","#123456")) {
            val c=AppearanceCatalog.palette(AppearanceSettings(p.id,accent,mode))
            assertTrue(AppearanceCatalog.contrast(c.text,c.panel)>=4.5)
            assertTrue(AppearanceCatalog.contrast(c.muted,c.panel)>=4.5)
            assertTrue(AppearanceCatalog.contrast(c.accent,AppearanceCatalog.blend(c.panel,c.accent,.14))>=4.5)
        }
    }
    @Test fun invalidStoredValuesCannotInjectColorsOrSetUnsafeBrightness() {
        val s=AppearanceCatalog.normalize(AppearanceSettings("missing","red;url(x)","0",true))
        assertEquals("graphite",s.preset);assertEquals("",s.accent);assertEquals("system",s.brightness)
        assertEquals(-1f,AppearanceCatalog.windowBrightness("garbage"),0f)
        assertEquals("#AABBCC",AppearanceCatalog.normalize(AppearanceSettings(accent="aabbcc")).accent)
    }
    @Test fun nightAppearanceChangesPaletteWithoutDestroyingContrast() {
        val day=AppearanceCatalog.palette(AppearanceSettings(preset="sand"));val night=AppearanceCatalog.palette(AppearanceSettings(preset="sand",brightness="night"))
        assertTrue(day.background!=night.background);assertTrue(day.text!=night.text)
        assertTrue(AppearanceCatalog.contrast(night.text,night.panel)>7)
        assertEquals(.25f,AppearanceCatalog.windowBrightness("night"),0f)
    }
}
