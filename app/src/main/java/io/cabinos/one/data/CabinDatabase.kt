package io.cabinos.one.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.cabinos.core.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "preferences")
data class PreferenceRecord(@PrimaryKey val id: Int = 1, val focus: Int = 25, val relax: Int = 30,
    val buffer: Int = 5, val voice: Boolean = true, val sharing: Boolean = false, val category: String = "전체") {
    fun domain() = Preferences(focus,relax,buffer,voice,sharing,category)
    companion object { fun from(p: Preferences) = PreferenceRecord(focus=p.focusMinutes,relax=p.relaxMinutes,buffer=p.departureBufferMinutes,voice=p.voiceEnabled,sharing=p.shareHazards,category=p.preferredCategory) }
}
@Entity(tableName = "sessions")
data class SessionRecord(@PrimaryKey val id:String, val kind:String, val started:Long, val duration:Long, val remaining:Long, val status:String) {
    fun domain() = CabinSession(id,SessionKind.valueOf(kind),started,duration,remaining,SessionStatus.valueOf(status))
    companion object { fun from(s:CabinSession) = SessionRecord(s.id,s.kind.name,s.startedAtMs,s.durationSeconds,s.remainingSeconds,s.status.name) }
}
@Entity(tableName = "checkpoint")
data class CheckpointRecord(@PrimaryKey val id:Int=1,val activeSessionId:String?=null,val departure:Long?=null,
    @ColumnInfo(defaultValue="0") val returnSeconds:Long=0,
    @ColumnInfo(defaultValue="0") val inboundSeconds:Long=0,
    val chargeTargetPercent:Int?=null,val arrivalTargetMs:Long?=null) {
    companion object {
        fun from(state:AppState)=CheckpointRecord(activeSessionId=state.session?.id,departure=state.schedule.departureAtMs,
            returnSeconds=state.schedule.returnSeconds,inboundSeconds=state.schedule.inboundSeconds,
            chargeTargetPercent=state.chargeTargetPercent,arrivalTargetMs=state.arrivalTargetMs)
    }
}
@Entity(tableName = "tasks")
data class TaskRecord(@PrimaryKey val id:String, val text:String, val done:Boolean=false)
@Dao
interface CabinDao {
    @Query("SELECT * FROM preferences WHERE id=1") suspend fun preferences(): PreferenceRecord?
    @Upsert suspend fun savePreferences(record:PreferenceRecord)
    @Query("SELECT * FROM checkpoint WHERE id=1") suspend fun checkpoint(): CheckpointRecord?
    @Upsert suspend fun saveCheckpoint(record:CheckpointRecord)
    @Query("SELECT * FROM sessions WHERE id=:id") suspend fun session(id:String): SessionRecord?
    @Query("SELECT * FROM sessions WHERE status IN ('COMPLETED','INTERRUPTED') ORDER BY started DESC LIMIT 100") suspend fun history(): List<SessionRecord>
    @Upsert suspend fun saveSession(record:SessionRecord)
    @Query("SELECT * FROM tasks ORDER BY rowid DESC") fun tasks(): Flow<List<TaskRecord>>
    @Upsert suspend fun saveTask(record:TaskRecord)
    @Query("UPDATE tasks SET done = NOT done WHERE id=:id") suspend fun toggleTask(id:String)
    @Query("DELETE FROM tasks WHERE id=:id") suspend fun deleteTask(id:String)
    @Query("DELETE FROM sessions WHERE id NOT IN (SELECT id FROM sessions WHERE status IN ('COMPLETED','INTERRUPTED') ORDER BY started DESC LIMIT 100) AND (:activeId IS NULL OR id != :activeId)")
    suspend fun pruneSessions(activeId: String?)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM preferences") suspend fun clearPreferences()
    @Query("DELETE FROM checkpoint") suspend fun clearCheckpoint()
    @Transaction suspend fun clearPersonalData() { clearTasks(); clearSessions(); clearPreferences(); clearCheckpoint() }
    @Transaction suspend fun saveState(state:AppState) {
        val preferences=PreferenceRecord.from(state.preferences)
        if(preferences()!=preferences)savePreferences(preferences)
        state.session?.let { val record=SessionRecord.from(it);if(session(it.id)!=record)saveSession(record) }
        val savedHistory=history().associateBy {it.id}
        val changed=state.history.map {SessionRecord.from(it)}.filter {savedHistory[it.id]!=it}
        changed.forEach {saveSession(it)}
        val checkpoint=CheckpointRecord.from(state)
        val previous=checkpoint()
        if(previous!=checkpoint)saveCheckpoint(checkpoint)
        if(changed.isNotEmpty() || previous?.activeSessionId!=checkpoint.activeSessionId)pruneSessions(state.session?.id)
    }
}
@Database(entities=[PreferenceRecord::class,SessionRecord::class,CheckpointRecord::class,TaskRecord::class],version=2,exportSchema=true)
abstract class CabinDatabase : RoomDatabase() {
    abstract fun dao(): CabinDao
    companion object {
        val MIGRATION_1_2=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE checkpoint ADD COLUMN returnSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE checkpoint ADD COLUMN inboundSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE checkpoint ADD COLUMN chargeTargetPercent INTEGER")
                db.execSQL("ALTER TABLE checkpoint ADD COLUMN arrivalTargetMs INTEGER")
            }
        }
    }
}
class RoomCabinSessionRepository(private val dao:CabinDao) : CabinSessionRepository {
    override suspend fun save(session:CabinSession) = dao.saveSession(SessionRecord.from(session))
    override suspend fun history() = dao.history().map { it.domain() }
}
