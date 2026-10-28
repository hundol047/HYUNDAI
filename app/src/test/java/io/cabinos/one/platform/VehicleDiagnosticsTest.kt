package io.cabinos.one.platform

import android.util.Log
import io.cabinos.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=android.app.Application::class)
class VehicleDiagnosticsTest {
    @Test fun scenarioEvidenceIncludesSessionAndNavigationInputs() {
        ShadowLog.setLoggable("CabinVehicle",Log.DEBUG)
        val state=AppState(nowMs=10000,sample=VehicleSample(timestampMs=10000,source=DataSource.VEHICLE),
            arrivalTargetMs=100000,session=CabinSession("test",SessionKind.FOCUS,10000,900,900))
        VehicleDiagnostics().record(state,PleosReadState(true,""),PleosEnergyState(),
            PleosNavigationState(true,true,"",remainingSeconds=30,remainingMeters=500,readAtMs=10000))
        val json=JSONObject(ShadowLog.getLogsForTag("CabinVehicle").last().msg)
        assertEquals("FOCUS",json.getString("sessionKind"));assertEquals(900,json.getInt("sessionDurationSeconds"))
        assertEquals(10000,json.getLong("navigationReadAtMs"));assertEquals(100000,json.getLong("arrivalTargetMs"))
    }
}
