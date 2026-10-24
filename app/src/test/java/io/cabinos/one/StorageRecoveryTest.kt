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
    private class Fixture(dao:(CabinDao)->CabinDao):AutoCloseable {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,CabinDatabase::class.java).allowMainThreadQueries().build()
        val engine=UnifiedVehicleContextEngine()
        val speech=AndroidSpeechProvider(context)
        val location=AndroidLocationProvider(context)
        val store=ViewModelStore()
        val preferences=RuntimePreferences(context).apply {scenario="실차 연결"}
        val vm=CabinViewModel(engine,SimulationVehicleProvider(preferences),VehicleStateProvider(context),
            PleosVehicleReader(null),PleosNavigation(null),PleosCabinReader(null),dao(db.dao()),speech,location,
            AndroidNavigationProvider(context,location),AmbientAudio(context),DepartureReminders(context),
            NearbyPlaces(context),LiveNavigation(context,engine,speech),preferences)
        init {store.put("test",vm)}
        override fun close() {store.clear();speech.close();db.close()}
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
}
