package com.mentalcoach.data.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.serialization.json.Json

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lang: String,
    val maxExercises: Int,
    val mood: String,
    val previousContext: String?,
    val profilePhotoPath: String?,
    val startedAt: String,
    val endedAt: String?,
    val screen: String,
    /** JSON of CheckinReading. */
    val checkinJson: String?,
    val greeting: String?,
    /** JSON of Suggestion. */
    val suggestionJson: String?,
    /** JSON of Logbook. */
    val logbookJson: String?,
    val crisisText: String?,
)

@Entity(
    tableName = "exercise_runs",
    primaryKeys = ["visitId", "position"],
    foreignKeys = [ForeignKey(VisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.CASCADE)],
)
data class ExerciseRunEntity(
    val visitId: String,
    val position: Int,
    val exerciseId: String,
    val title: String,
    val intro: String,
    val minutes: Int,
    val steps: List<String>,
    val photoPrompts: List<String>,
    val startedAt: String?,
    val endedAt: String?,
    val photoPaths: List<String>,
    val selectedPhotos: List<Int>,
    val moodNow: Int?,
    @ColumnInfo(defaultValue = "0") val guided: Boolean = false,
    @ColumnInfo(defaultValue = "0") val stepIndex: Int = 0,
    /** JSON list of ExerciseNote. */
    @ColumnInfo(defaultValue = "[]") val notesJson: String = "[]",
)

@Entity(
    tableName = "chat_turns",
    primaryKeys = ["visitId", "exercisePosition", "seq"],
    foreignKeys = [ForeignKey(VisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId")],
)
data class ChatTurnEntity(
    val visitId: String,
    val exercisePosition: Int,
    val seq: Int,
    val role: String,
    val text: String,
)

class Converters {
    @TypeConverter
    fun fromStrings(v: List<String>): String = Json.encodeToString(v)

    @TypeConverter
    fun toStrings(v: String): List<String> = Json.decodeFromString(v)

    @TypeConverter
    fun fromInts(v: List<Int>): String = Json.encodeToString(v)

    @TypeConverter
    fun toInts(v: String): List<Int> = Json.decodeFromString(v)
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestVisit(): VisitEntity?

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun visit(id: String): VisitEntity?

    @Query("SELECT * FROM exercise_runs WHERE visitId = :visitId ORDER BY position")
    suspend fun runs(visitId: String): List<ExerciseRunEntity>

    @Query("SELECT * FROM chat_turns WHERE visitId = :visitId ORDER BY exercisePosition, seq")
    suspend fun turns(visitId: String): List<ChatTurnEntity>

    @Upsert
    suspend fun upsertVisit(v: VisitEntity)

    @Insert
    suspend fun insertRuns(r: List<ExerciseRunEntity>)

    @Insert
    suspend fun insertTurns(t: List<ChatTurnEntity>)

    @Query("DELETE FROM exercise_runs WHERE visitId = :visitId")
    suspend fun deleteRuns(visitId: String)

    @Query("DELETE FROM chat_turns WHERE visitId = :visitId")
    suspend fun deleteTurns(visitId: String)

    @Query("DELETE FROM visits WHERE id = :id")
    suspend fun deleteVisit(id: String)

    @Transaction
    suspend fun replace(
        v: VisitEntity,
        runs: List<ExerciseRunEntity>,
        turns: List<ChatTurnEntity>,
    ) {
        upsertVisit(v)
        deleteTurns(v.id)
        deleteRuns(v.id)
        insertRuns(runs)
        insertTurns(turns)
    }
}

@Database(
    entities = [VisitEntity::class, ExerciseRunEntity::class, ChatTurnEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class VisitDatabase : RoomDatabase() {
    abstract fun visits(): VisitDao
}
