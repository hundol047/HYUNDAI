package io.cabinos.one

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import io.cabinos.one.data.*
import io.cabinos.one.platform.*
import io.cabinos.one.ui.CabinViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class StorageRecoveryTest {
    private class Fixture(network:Boolean=false,dao:(CabinDao)->CabinDao):AutoCloseable {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,CabinDatabase::class.java).allowMainThreadQueries().build()
        var now=System.currentTimeMillis()
        val engine=UnifiedVehicleContextEngine({now})
        fun park() {
            engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
            now+=1600
            engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
            assertTrue(engine.state.value.parkedAllowed)
        }
        val speech=AndroidSpeechProvider(context)
        val location=AndroidLocationProvider(context)
        val store=ViewModelStore()
        val preferences=RuntimePreferences(context).apply {scenario="실차 연결"}
        val vm=CabinViewModel(engine,SimulationVehicleProvider(preferences),VehicleStateProvider(context),
            PleosVehicleReader(null),PleosNavigation(null),PleosCabinReader(null),dao(db.dao()),speech,location,
            AndroidNavigationProvider(context,location),AmbientAudio(context),DepartureReminders(context),
            NearbyPlaces(context),LiveNavigation(context,engine,speech),preferences,if(network)NetworkMonitor(context) else null)
        init {store.put("test",vm)}
        override fun close() {store.clear();speech.close();db.close()}
    }
    @Test fun taskRecoveryCannotHideAnUnrecoveredPlanReadFailure()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var attempts=0
        try {
            Fixture {delegate -> object:CabinDao by delegate {
                override suspend fun checkpoint():CheckpointRecord?=throw SQLiteException("unreadable plan")
                override fun tasks():Flow<List<TaskRecord>> = flow {
                    if(++attempts==1)throw SQLiteException("temporary task failure")
                    emit(emptyList())
                }
            }}.use {f->
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {f.vm.tasks.collect()}
                runCurrent();advanceTimeBy(5000);runCurrent()
                assertTrue(f.vm.storageStatus.value?.contains("저장된 계획") == true)
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun failedDeletionStopsAutomaticWritesAndRetryCanComplete()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var writes=0;var failDelete=true
        try {
            Fixture {delegate -> object:CabinDao by delegate {
                override suspend fun checkpoint():CheckpointRecord?=null
                override suspend fun preferences():PreferenceRecord?=null
                override suspend fun history()=emptyList<SessionRecord>()
                override suspend fun saveState(state:AppState) {writes++}
                override suspend fun clearPersonalData() {if(failDelete)throw SQLiteException("delete failed")}
            }}.use {f->
                runCurrent();f.park();f.vm.waiting(30);runCurrent()
                val before=writes
                f.vm.clearPersonalData();runCurrent()
                assertEquals(before,writes)
                assertTrue(f.vm.storageStatus.value?.contains("삭제") == true)
                f.vm.waiting(40);runCurrent();assertEquals(before,writes)
                failDelete=false;f.vm.clearPersonalData();runCurrent()
                assertTrue(writes>before)
                assertTrue(f.vm.storageStatus.value?.contains("실패") != true)
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun unreadableCheckpointDoesNotCrashOrOverwriteSavedDataAndVehicleMonitoringContinues()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var writes=0
        try {
            Fixture {delegate -> object:CabinDao by delegate {
                override suspend fun checkpoint():CheckpointRecord?=throw SQLiteException("storage unavailable")
                override suspend fun saveState(state:AppState) {writes++}
            }}.use {f->
                runCurrent()
                assertNotNull(f.vm.storageStatus.value)
                assertNotNull(f.vm.state.value.sample)
                assertFalse(f.vm.state.value.parkedAllowed)
                advanceTimeBy(2000);runCurrent()
                assertEquals(0,writes)
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun taskReadFailureIsReportedAndRecoversWithoutRestartingTheApp()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var attempts=0
        try {
            Fixture {delegate -> object:CabinDao by delegate {
                override suspend fun checkpoint():CheckpointRecord?=null
                override suspend fun preferences():PreferenceRecord?=null
                override suspend fun history()=emptyList<SessionRecord>()
                override suspend fun saveState(state:AppState) {}
                override fun tasks():Flow<List<TaskRecord>> = flow {
                    attempts++
                    if(attempts==1)throw SQLiteException("temporary storage read failure")
                    emit(listOf(TaskRecord("recovered","복구된 할 일")))
                }
            }}.use {f->
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {f.vm.tasks.collect()}
                runCurrent();assertTrue(f.vm.storageStatus.value?.contains("할 일") == true)
                advanceTimeBy(5000);runCurrent()
                assertEquals("recovered",f.vm.tasks.value.single().id)
                assertFalse(f.vm.state.value.parkedAllowed)
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun changingOnlyChargeTargetStillPersistsPlan()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var saved:CheckpointRecord?=null
        try {
            Fixture {delegate -> object:CabinDao by delegate {
                override suspend fun checkpoint():CheckpointRecord?=null
                override suspend fun preferences():PreferenceRecord?=null
                override suspend fun history()=emptyList<SessionRecord>()
                override suspend fun saveState(state:AppState) {saved=CheckpointRecord.from(state)}
            }}.use {f ->
                runCurrent();f.park();runCurrent()
                assertTrue(f.engine.setChargeTarget(85));runCurrent()
                assertEquals(85,saved?.chargeTargetPercent)
                assertTrue(f.engine.setArrivalInMinutes(60));runCurrent()
                assertEquals(f.engine.state.value.arrivalTargetMs,saved?.arrivalTargetMs)
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun operatingSystemConnectivityChangesReachApplicationState()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture(network=true) {delegate -> object:CabinDao by delegate {
                override suspend fun checkpoint():CheckpointRecord?=null
                override suspend fun preferences():PreferenceRecord?=null
                override suspend fun history()=emptyList<SessionRecord>()
                override suspend fun saveState(state:AppState) {}
            }}.use {f ->
                runCurrent()
                val manager=f.context.getSystemService(android.net.ConnectivityManager::class.java)
                val shadow=org.robolectric.Shadows.shadowOf(manager)
                val network=manager.activeNetwork!!
                val capabilities=android.net.NetworkCapabilities().apply {
                    org.robolectric.Shadows.shadowOf(this).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    org.robolectric.Shadows.shadowOf(this).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
                shadow.setNetworkCapabilities(network,capabilities)
                val callback=shadow.networkCallbacks.single()
                callback.onCapabilitiesChanged(network,capabilities);runCurrent()
                assertTrue(f.engine.state.value.networkAvailable)
                callback.onLost(network);runCurrent()
                assertFalse(f.engine.state.value.networkAvailable)
                callback.onAvailable(network);runCurrent()
                assertTrue(f.engine.state.value.networkAvailable)
            }
        } finally {Dispatchers.resetMain()}
    }

    private class TaskStore(delegate:CabinDao):CabinDao by delegate {
        val rows=mutableMapOf<String,TaskRecord>()
        var fail=false
        var planFails=false
        override suspend fun checkpoint():CheckpointRecord?=null
        override suspend fun preferences():PreferenceRecord?=null
        override suspend fun history()=emptyList<SessionRecord>()
        override suspend fun saveState(state:AppState) {if(planFails)throw SQLiteException("plan write failed")}
        override suspend fun saveTask(record:TaskRecord) {if(fail)throw SQLiteException("write failed");rows[record.id]=record}
        override suspend fun deleteTask(id:String) {rows.remove(id)}
        override suspend fun toggleTask(id:String) {
            if(fail)throw SQLiteException("write failed")
            rows[id]?.let {rows[id]=it.copy(done=!it.done)}
        }
    }
    @Test fun rapidCheckboxTapsUseStoredStateRatherThanStaleScreenRow()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        lateinit var tasks:TaskStore
        try {
            Fixture {TaskStore(it).also {tasks=it}}.use {f ->
                runCurrent();f.park();runCurrent()
                val row=TaskRecord("task","확인");tasks.rows[row.id]=row
                f.vm.toggleTask(row);f.vm.toggleTask(row);runCurrent()
                assertFalse(tasks.rows.getValue(row.id).done)
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun queuedCheckboxTapCannotRecreateDeletedTask()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        lateinit var tasks:TaskStore
        try {
            Fixture {TaskStore(it).also {tasks=it}}.use {f ->
                runCurrent();f.park();runCurrent()
                val row=TaskRecord("task","확인");tasks.rows[row.id]=row
                f.vm.deleteTask(row);f.vm.toggleTask(row);runCurrent()
                assertFalse(tasks.rows.containsKey(row.id))
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun successfulPlanAutosaveDoesNotHideFailedTaskWrite()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        lateinit var tasks:TaskStore
        try {
            Fixture {TaskStore(it).also {tasks=it}}.use {f ->
                runCurrent();f.park();runCurrent()
                tasks.fail=true;f.vm.addTask("저장 실패");runCurrent()
                assertTrue(f.vm.storageStatus.value?.contains("할 일 변경") == true)
                f.vm.waiting(30);runCurrent()
                assertTrue(f.vm.storageStatus.value?.contains("할 일 변경") == true)
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun successfulTaskRetryClearsOnlyItsOwnWriteFailure()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        lateinit var tasks:TaskStore
        try {
            Fixture {TaskStore(it).also {tasks=it}}.use {f ->
                runCurrent();f.park();runCurrent()
                tasks.fail=true;f.vm.addTask("확인");runCurrent()
                assertNotNull(f.vm.storageStatus.value)
                tasks.fail=false;f.vm.addTask("확인");runCurrent()
                assertNull(f.vm.storageStatus.value)
            }
        } finally {Dispatchers.resetMain()}
    }

    @Test fun successfulTaskWriteDoesNotClearSeparatePlanSaveFailure()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        lateinit var tasks:TaskStore
        try {
            Fixture {TaskStore(it).also {tasks=it}}.use {f ->
                runCurrent();f.park();runCurrent()
                tasks.planFails=true;f.vm.waiting(30);runCurrent()
                assertTrue(f.vm.storageStatus.value?.contains("최근 변경") == true)
                tasks.fail=true;f.vm.addTask("확인");runCurrent()
                tasks.fail=false;f.vm.addTask("확인");runCurrent()
                assertTrue(f.vm.storageStatus.value?.contains("최근 변경") == true)
            }
        } finally {Dispatchers.resetMain()}
    }

}
