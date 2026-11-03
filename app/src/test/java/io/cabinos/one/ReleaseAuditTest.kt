package io.cabinos.one

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import io.cabinos.one.data.*
import io.cabinos.one.platform.DepartureReminders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ReleaseAuditTest {
    private var now=System.currentTimeMillis()
    private fun park(e:UnifiedVehicleContextEngine) {
        e.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        now+=1600;e.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        assertTrue(e.state.value.parkedAllowed)
    }
    private fun restored(dao:CabinDao):UnifiedVehicleContextEngine=runBlocking {
        val checkpoint=dao.checkpoint()
        UnifiedVehicleContextEngine({now}).apply {
            restore(dao.preferences()!!.domain(),checkpoint?.activeSessionId?.let {dao.session(it)?.domain()},dao.history().map {it.domain()},checkpoint?.departure,
                checkpoint?.returnSeconds ?: 0,checkpoint?.inboundSeconds ?: 0,checkpoint?.chargeTargetPercent,checkpoint?.arrivalTargetMs)
        }
    }
    @Test fun restartMustNotPostponePreviouslySavedReturnReminder()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,CabinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val e=UnifiedVehicleContextEngine({now});park(e);e.setWaitingMinutes(40)
            assertTrue(e.selectActivity("cafe"));db.dao().saveState(e.state.value)
            val reminders=DepartureReminders(context){now};reminders.sync(e.state.value)
            val prefs=context.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE)
            val before=prefs.getLong("alert",0)
            val recovered=restored(db.dao());park(recovered);reminders.sync(recovered.state.value)
            val after=prefs.getLong("alert",0)
            println("RETURN_REMINDER before=$before after=$after delayedSeconds=${(after-before)/1000}")
            assertEquals("Restoring must preserve the saved return reserve",before,after)
        } finally {db.close()}
    }
    @Test fun restartMustRetainUserChargeAndArrivalTargets()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,CabinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val e=UnifiedVehicleContextEngine({now});park(e)
            assertTrue(e.setChargeTarget(80));assertTrue(e.setArrivalInMinutes(60))
            db.dao().saveState(e.state.value)
            val recovered=restored(db.dao())
            println("TARGETS before=${e.state.value.chargeTargetPercent}/${e.state.value.arrivalTargetMs} after=${recovered.state.value.chargeTargetPercent}/${recovered.state.value.arrivalTargetMs}")
            assertEquals(80,recovered.state.value.chargeTargetPercent)
            assertEquals(e.state.value.arrivalTargetMs,recovered.state.value.arrivalTargetMs)
        } finally {db.close()}
    }
    @Test fun timerTickShouldNotRewriteAllUnchangedHistory()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,CabinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val history=(1..100).map {CabinSession("history-$it",SessionKind.FOCUS,now-it*1000,60,0,SessionStatus.COMPLETED)}
            val e=UnifiedVehicleContextEngine({now});e.restore(Preferences(),null,history,null);park(e);e.setWaitingMinutes(40);e.startSession(SessionKind.FOCUS,25)
            var writes=0
            val dao=object:CabinDao by db.dao() {
                override suspend fun saveSession(record:SessionRecord) {writes++;db.dao().saveSession(record)}
                override suspend fun saveState(state:AppState) {super<CabinDao>.saveState(state)}
            }
            dao.saveState(e.state.value);writes=0
            now+=1000;e.tick();dao.saveState(e.state.value)
            println("SESSION_WRITES_FOR_ONE_TIMER_TICK=$writes")
            assertEquals("Only the active session changed",1,writes)
        }finally {db.close()}
    }
    @Test fun restoredReserveSurvivesSearchRefreshUntilUserChoosesCabin()=runBlocking {
        val e=UnifiedVehicleContextEngine({now})
        e.restore(Preferences(),null,emptyList(),now+2_400_000,600,420,80,now+3_600_000)
        assertFalse(e.state.value.parkedAllowed)
        park(e)
        assertTrue(e.updateNearbyActivities(MapPosition(),emptyList()))
        assertEquals(600L,e.state.value.schedule.returnSeconds)
        assertEquals(420L,e.state.value.schedule.inboundSeconds)
        e.stayInCabin()
        assertEquals(0L,e.state.value.schedule.returnSeconds)
        assertEquals(0L,e.state.value.schedule.inboundSeconds)
    }
    @Test fun expiredPlanDoesNotRestoreWalkReserveOrArrivalTarget() {
        val e=UnifiedVehicleContextEngine({now})
        e.restore(Preferences(),null,emptyList(),now-1,600,420,80,now-1)
        assertNull(e.state.value.schedule.departureAtMs)
        assertNull(e.state.value.arrivalTargetMs)
        assertEquals(80,e.state.value.chargeTargetPercent)
        assertEquals(0L,e.state.value.schedule.returnSeconds)
    }
    @Test fun confirmedDrivingClearsRestoredReserve() {
        val e=UnifiedVehicleContextEngine({now})
        e.restore(Preferences(),null,emptyList(),now+2_400_000,600,420)
        park(e)
        e.acceptVehicle(VehicleSample(speedKph=20.0,gear=Gear.DRIVE,timestampMs=now))
        assertEquals(0L,e.state.value.schedule.returnSeconds)
        assertEquals(0L,e.state.value.schedule.inboundSeconds)
    }

}
