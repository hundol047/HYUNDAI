package io.cabinos.one.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class AppearancePersistenceTest {
    @Test fun appearanceSurvivesPreferenceRecreationWithoutChangingVehicleSource() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val p=RuntimePreferences(context);p.scenario="실차 연결"
        val settings=AppearanceSettings("sand","#112233","night",false);p.appearance=settings
        val restored=RuntimePreferences(context)
        assertEquals(settings,restored.appearance);assertEquals("실차 연결",restored.scenario)
        restored.appearance=AppearanceSettings();assertEquals("실차 연결",restored.scenario)
    }
    @Test fun corruptedStoredAppearanceFallsBackToSafeValues() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("vehicle-source",Context.MODE_PRIVATE).edit().putString("appearance.preset","unknown").putString("appearance.accent","#bad").putString("appearance.brightness","-10").apply()
        assertEquals(AppearanceSettings(),RuntimePreferences(context).appearance)
    }
}
