package io.cabinos.one

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import io.cabinos.one.data.*
import io.cabinos.one.platform.*
import io.cabinos.one.ui.CabinViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class PendingAudioTest {
    private class Fixture:AutoCloseable {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val started=CountDownLatch(1)
        val release=CountDownLatch(1)
        val audio=AmbientAudio(context) {
            started.countDown()
            check(release.await(10,TimeUnit.SECONDS))
            ShortArray(44100) {if(it%40<20)100 else -100}
        }
        val db=Room.inMemoryDatabaseBuilder(context,CabinDatabase::class.java).allowMainThreadQueries().build()
        val dao=object:CabinDao by db.dao() {
            override suspend fun checkpoint():CheckpointRecord?=null
            override suspend fun preferences():PreferenceRecord?=null
            override suspend fun history()=emptyList<SessionRecord>()
            override fun tasks()=flowOf(emptyList<TaskRecord>())
            override suspend fun saveState(state:AppState) {}
        }
        var now=System.currentTimeMillis()
        val engine=UnifiedVehicleContextEngine({now})
        val speech=AndroidSpeechProvider(context)
        val location=AndroidLocationProvider(context)
        val nav=LiveNavigation(context,engine,speech,clock={now})
        val preferences=RuntimePreferences(context).apply {scenario="실차 연결"}
        val store=ViewModelStore()
        val vm=CabinViewModel(engine,SimulationVehicleProvider(preferences),VehicleStateProvider(context),
            PleosVehicleReader(null),PleosNavigation(null),PleosCabinReader(null),dao,speech,location,
            AndroidNavigationProvider(context,location),audio,DepartureReminders(context),NearbyPlaces(context),nav,preferences)
        init {store.put("test",vm);shadowOf(context.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)}
        fun park() {
            engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
            now+=1600;engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
            engine.setWaitingMinutes(30);engine.startSession(SessionKind.FOCUS,20)
        }
        override fun close(){release.countDown();store.clear();nav.close();speech.close();audio.stop();db.close()}
    }
    @Test fun namedSoundStopCommandNeverStartsPlayback()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {Fixture().use {f->
            runCurrent();f.park();runCurrent()
            f.release.countDown()
            f.vm.command("빗소리 꺼줘");runCurrent()
            assertEquals("배경 소리를 껐습니다.",f.vm.reply.value)
            assertEquals(1L,f.started.count)
            assertFalse(f.audio.playing)
        }} finally {Dispatchers.resetMain()}
    }
    @Test fun lateAudioCannotRestartAfterPauseAndResume()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {Fixture().use {f->
            runCurrent();f.park();runCurrent()
            f.vm.toggleAudio("빗소리");runCurrent()
            val pending=f.vm.viewModelScope.coroutineContext[Job]!!.children.last()
            assertTrue(f.started.await(5,TimeUnit.SECONDS))
            f.engine.pauseOrResume();runCurrent();f.engine.pauseOrResume();runCurrent()
            assertEquals(SessionStatus.RUNNING,f.engine.state.value.session?.status)
            f.release.countDown();pending.join();runCurrent()
            assertFalse(f.audio.playing);assertNull(f.vm.audioPreset.value)
        }} finally {Dispatchers.resetMain()}
    }
    @Test fun lateAudioCannotCrossIntoANewSession()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {Fixture().use {f->
            runCurrent();f.park();runCurrent()
            f.vm.toggleAudio("빗소리");runCurrent()
            val pending=f.vm.viewModelScope.coroutineContext[Job]!!.children.last()
            assertTrue(f.started.await(5,TimeUnit.SECONDS))
            val old=f.engine.state.value.session!!.id
            f.engine.startSession(SessionKind.RELAX,10);runCurrent()
            assertNotEquals(old,f.engine.state.value.session!!.id)
            f.release.countDown();pending.join();runCurrent()
            assertFalse(f.audio.playing);assertNull(f.vm.audioPreset.value)
        }} finally {Dispatchers.resetMain()}
    }
}
