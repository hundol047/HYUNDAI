package io.cabinos.one

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.cabinos.one.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class PlanMigrationTest {
    @Test fun versionOneDatabaseUpgradesWithoutLosingSavedPlanOrHistory()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="migration-test.db"
        context.deleteDatabase(name)
        val path=context.getDatabasePath(name).apply {parentFile!!.mkdirs()}
        SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            val schema=JSONObject(File("schemas/io.cabinos.one.data.CabinDatabase/1.json").readText()).getJSONObject("database")
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity=entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO preferences VALUES(1,25,30,5,1,0,'카페')")
            old.execSQL("INSERT INTO sessions VALUES('session','FOCUS',1000,600,300,'PAUSED')")
            old.execSQL("INSERT INTO checkpoint VALUES(1,'session',9999999999999)")
            old.execSQL("INSERT INTO tasks VALUES('task','확인할 일',0)")
            old.version=1
        }
        val db=Room.databaseBuilder(context,CabinDatabase::class.java,name)
            .addMigrations(CabinDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val checkpoint=db.dao().checkpoint()!!
            assertEquals(9999999999999L,checkpoint.departure)
            assertEquals("session",checkpoint.activeSessionId)
            assertEquals(0L,checkpoint.returnSeconds)
            assertNull(checkpoint.chargeTargetPercent)
            assertEquals("카페",db.dao().preferences()!!.category)
            assertEquals(300L,db.dao().session("session")!!.remaining)
            db.openHelper.readableDatabase.query("SELECT text FROM tasks").use {row ->
                assertTrue(row.moveToFirst());assertEquals("확인할 일",row.getString(0))
            }
        } finally {db.close();context.deleteDatabase(name)}
    }
}
