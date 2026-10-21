package io.cabinos.one.data

import androidx.room.*
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
data class CheckpointRecord(@PrimaryKey val id:Int=1,val activeSessionId:String?=null,val departure:Long?=null)
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
    @Query("DELETE FROM tasks WHERE id=:id") suspend fun deleteTask(id:String)
    @Query("DELETE FROM sessions WHERE id NOT IN (SELECT id FROM sessions WHERE status IN ('COMPLETED','INTERRUPTED') ORDER BY started DESC LIMIT 100) AND (:activeId IS NULL OR id != :activeId)")
    suspend fun pruneSessions(activeId: String?)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM preferences") suspend fun clearPreferences()
    @Query("DELETE FROM checkpoint") suspend fun clearCheckpoint()
    @Transaction suspend fun clearPersonalData() { clearTasks(); clearSessions(); clearPreferences(); clearCheckpoint() }
    @Transaction suspend fun saveState(state:AppState) {
        savePreferences(PreferenceRecord.from(state.preferences))
        state.session?.let { saveSession(SessionRecord.from(it)) }
        state.history.forEach { saveSession(SessionRecord.from(it)) }
        saveCheckpoint(CheckpointRecord(activeSessionId=state.session?.id,departure=state.schedule.departureAtMs))
        pruneSessions(state.session?.id)
    }
}
@Database(entities=[PreferenceRecord::class,SessionRecord::class,CheckpointRecord::class,TaskRecord::class],version=1,exportSchema=true)
abstract class CabinDatabase : RoomDatabase() { abstract fun dao(): CabinDao }
class RoomCabinSessionRepository(private val dao:CabinDao) : CabinSessionRepository {
    override suspend fun save(session:CabinSession) = dao.saveSession(SessionRecord.from(session))
    override suspend fun history() = dao.history().map { it.domain() }
}
