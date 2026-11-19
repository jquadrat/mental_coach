package com.mentalcoach.data.repo

import com.mentalcoach.data.db.ChatTurnEntity
import com.mentalcoach.data.db.ExerciseRunEntity
import com.mentalcoach.data.db.VisitDao
import com.mentalcoach.data.db.VisitEntity
import com.mentalcoach.domain.ChatTurn
import com.mentalcoach.domain.CheckinAnswer
import com.mentalcoach.domain.CheckinReading
import com.mentalcoach.domain.ExerciseNote
import com.mentalcoach.domain.ExerciseRun
import com.mentalcoach.domain.Lang
import com.mentalcoach.domain.Logbook
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Screen
import com.mentalcoach.domain.Suggestion
import com.mentalcoach.domain.Visit
import kotlinx.serialization.json.Json

interface VisitRepository {
    /** The visit in progress, if any (to resume after process death). */
    suspend fun current(): Visit?

    suspend fun save(visit: Visit)

    /** Deletes the visit's rows and all its photo files. */
    suspend fun delete(visit: Visit)
}

/** Room-backed repository. Photos live as files; [deleteFiles] removes them with the visit. */
class RoomVisitRepository(
    private val dao: VisitDao,
    private val deleteFiles: (visitId: String) -> Unit,
) : VisitRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun current(): Visit? {
        val v = dao.latestVisit() ?: return null
        val turns = dao.turns(v.id).groupBy { it.exercisePosition }
        val runs =
            dao.runs(v.id).map { r ->
                ExerciseRun(
                    exerciseId = r.exerciseId,
                    title = r.title,
                    intro = r.intro,
                    minutes = r.minutes,
                    steps = r.steps,
                    photoPrompts = r.photoPrompts,
                    guided = r.guided,
                    stepIndex = r.stepIndex,
                    notes = json.decodeFromString<List<ExerciseNote>>(r.notesJson),
                    startedAt = r.startedAt,
                    endedAt = r.endedAt,
                    photos = r.photoPaths,
                    selected = r.selectedPhotos,
                    chat = turns[r.position].orEmpty().map { ChatTurn(if (it.role == "coach") Role.COACH else Role.USER, it.text) },
                    moodNow = r.moodNow,
                )
            }
        return Visit(
            id = v.id,
            name = v.name,
            lang = Lang.from(v.lang),
            maxExercises = v.maxExercises,
            mood = v.mood,
            previousContext = v.previousContext,
            profilePhoto = v.profilePhotoPath,
            startedAt = v.startedAt,
            endedAt = v.endedAt,
            screen = Screen.from(v.screen),
            checkin = v.checkinJson?.let { json.decodeFromString<CheckinReading>(it) },
            greeting = v.greeting,
            answers = json.decodeFromString<List<CheckinAnswer>>(v.answersJson),
            exercises = runs,
            suggestion = v.suggestionJson?.let { json.decodeFromString<Suggestion>(it) },
            logbook = v.logbookJson?.let { json.decodeFromString<Logbook>(it) },
            crisisText = v.crisisText,
        )
    }

    override suspend fun save(visit: Visit) {
        val v =
            VisitEntity(
                id = visit.id,
                name = visit.name,
                lang = visit.lang.code,
                maxExercises = visit.maxExercises,
                mood = visit.mood,
                previousContext = visit.previousContext,
                profilePhotoPath = visit.profilePhoto,
                startedAt = visit.startedAt,
                endedAt = visit.endedAt,
                screen = visit.screen.route,
                checkinJson = visit.checkin?.let { json.encodeToString(it) },
                greeting = visit.greeting,
                suggestionJson = visit.suggestion?.let { json.encodeToString(it) },
                logbookJson = visit.logbook?.let { json.encodeToString(it) },
                crisisText = visit.crisisText,
                answersJson = json.encodeToString(visit.answers),
            )
        val runs =
            visit.exercises.mapIndexed { i, r ->
                ExerciseRunEntity(
                    visit.id, i, r.exerciseId, r.title, r.intro, r.minutes, r.steps, r.photoPrompts,
                    r.startedAt, r.endedAt, r.photos, r.selected, r.moodNow,
                    guided = r.guided,
                    stepIndex = r.stepIndex,
                    notesJson = json.encodeToString(r.notes),
                )
            }
        val turns =
            visit.exercises.flatMapIndexed { i, r ->
                r.chat.mapIndexed { seq, t -> ChatTurnEntity(visit.id, i, seq, if (t.role == Role.COACH) "coach" else "user", t.text) }
            }
        dao.replace(v, runs, turns)
    }

    override suspend fun delete(visit: Visit) {
        dao.deleteVisit(visit.id)
        deleteFiles(visit.id)
    }
}

class InMemoryVisitRepository : VisitRepository {
    var stored: Visit? = null
    val deleted = mutableListOf<String>()

    override suspend fun current() = stored

    override suspend fun save(visit: Visit) {
        stored = visit
    }

    override suspend fun delete(visit: Visit) {
        if (stored?.id == visit.id) stored = null
        deleted += visit.id
    }
}
