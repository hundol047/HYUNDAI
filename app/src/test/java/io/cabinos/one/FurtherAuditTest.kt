package io.cabinos.one

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import io.cabinos.one.platform.DepartureReminders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class FurtherAuditTest {
    private var now=System.currentTimeMillis()
    private fun park(e:UnifiedVehicleContextEngine) {
        e.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        now+=1600;e.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        assertTrue(e.state.value.parkedAllowed)
    }
    @Test fun searchRefreshMustNotPostponePlannedReturnReminder()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val e=UnifiedVehicleContextEngine({now});park(e);e.setWaitingMinutes(40)
        assertTrue(e.selectActivity("cafe"))
        val reminders=DepartureReminders(context){now};reminders.sync(e.state.value)
        val prefs=context.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE)
        val before=prefs.getLong("alert",0)
        val reserve=e.state.value.schedule.returnSeconds
        e.updateNearbyActivities(MapPosition(37.0,127.0),emptyList())
        reminders.sync(e.state.value)
        println("REFRESH reserve=$reserve -> ${e.state.value.schedule.returnSeconds}; alert shift=${(prefs.getLong("alert",0)-before)/1000}s")
        assertEquals(reserve,e.state.value.schedule.returnSeconds)
        assertEquals(before,prefs.getLong("alert",0))
    }
}
