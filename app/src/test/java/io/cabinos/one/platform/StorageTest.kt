package io.cabinos.one.platform

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import io.cabinos.one.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class StorageTest {
    @Test fun retentionKeepsOneHundredHistoryEntriesAndOlderActiveSession() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),CabinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val active=CabinSession("active",SessionKind.FOCUS,0,1500,900,SessionStatus.PAUSED)
            val history=(1..105).map { CabinSession("history-$it",SessionKind.RELAX,it.toLong(),600,0,SessionStatus.COMPLETED) }
            db.dao().saveState(AppState(session=active,history=history))
            assertEquals(100,db.dao().history().size)
            assertNull(db.dao().session("history-1"))
            assertNotNull(db.dao().session("active"))
            assertEquals("active",db.dao().checkpoint()!!.activeSessionId)
        } finally { db.close() }
    }
    @Test fun deletingPersonalDataRemovesTasksPreferencesSessionsAndCheckpoint() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),CabinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.dao()
            dao.saveTask(TaskRecord("a","private task"))
            dao.saveState(AppState(preferences=Preferences(focusMinutes=45),history=listOf(CabinSession("done",SessionKind.FOCUS,1,60,0,SessionStatus.COMPLETED))))
            dao.clearPersonalData()
            assertNull(dao.preferences());assertNull(dao.checkpoint());assertNull(dao.session("done"));assertTrue(dao.history().isEmpty())
        } finally { db.close() }
    }
    @Test fun databaseToggleIsAtomicAndNeverInsertsMissingRows() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),CabinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.dao()
            dao.saveTask(TaskRecord("task","확인"))
            dao.toggleTask("task")
            db.openHelper.readableDatabase.query("SELECT done FROM tasks WHERE id='task'").use {r ->
                assertTrue(r.moveToFirst());assertEquals(1,r.getInt(0))
            }
            dao.toggleTask("task")
            db.openHelper.readableDatabase.query("SELECT done FROM tasks WHERE id='task'").use {r ->
                assertTrue(r.moveToFirst());assertEquals(0,r.getInt(0))
            }
            dao.deleteTask("task");dao.toggleTask("task")
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM tasks").use {r ->
                assertTrue(r.moveToFirst());assertEquals(0,r.getInt(0))
            }
        } finally {db.close()}
    }

}
