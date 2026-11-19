package com.mentalcoach.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Lang(val code: String) {
    EN("en"),
    DE("de"),
    ;

    companion object {
        fun from(code: String?): Lang = entries.firstOrNull { it.code == code } ?: EN
    }
}

/** The flow step a visit is on. Also the navigation route. */
enum class Screen(val route: String) {
    CHECKIN("checkin"),
    INTRO("intro"),
    EXERCISE("exercise"),
    CHAT("chat"),
    LOGBOOK("logbook"),
    CRISIS("crisis"),
    ;

    companion object {
        fun from(route: String?): Screen = entries.firstOrNull { it.route == route } ?: CHECKIN
    }
}

@Serializable
data class LocalizedText(val en: String, val de: String) {
    operator fun get(lang: Lang): String = if (lang == Lang.DE) de else en
}

@Serializable
data class LocalizedList(val en: List<String>, val de: List<String>) {
    operator fun get(lang: Lang): List<String> = if (lang == Lang.DE) de else en
}

/** An exercise from the bundled library (assets/exercises.json). */
@Serializable
data class Exercise(
    val id: String,
    val minutes: Int,
    val goodFor: String,
    val title: LocalizedText,
    val steps: LocalizedList,
    val photoPrompts: LocalizedList,
    /** Guided exercises go step by step; the visitor answers each step by voice, text or photo. */
    val guided: Boolean = false,
)

@Serializable
enum class Role {
    @SerialName("coach")
    COACH,

    @SerialName("user")
    USER,
}

@Serializable
data class ChatTurn(val role: Role, val text: String)

/** Something the visitor said or typed during an exercise, for the logbook. [step] is 0-based, if any. */
@Serializable
data class ExerciseNote(val text: String, val step: Int? = null)

@Serializable
data class CheckinReading(
    val summary: String,
    val feelings: List<String>,
    val energy: Int,
    val mood: Int,
)

@Serializable
data class Suggestion(
    val action: String,
    val exerciseId: String? = null,
    val why: String = "",
) {
    val isNext get() = action == ACTION_NEXT

    companion object {
        const val ACTION_NEXT = "next"
        const val ACTION_END = "end"
    }
}

data class ExerciseRun(
    val exerciseId: String,
    val title: String,
    val intro: String,
    val minutes: Int,
    val steps: List<String>,
    val photoPrompts: List<String>,
    val guided: Boolean = false,
    val stepIndex: Int = 0,
    val notes: List<ExerciseNote> = emptyList(),
    val startedAt: String? = null,
    val endedAt: String? = null,
    val photos: List<String> = emptyList(),
    val selected: List<Int> = emptyList(),
    val chat: List<ChatTurn> = emptyList(),
    val moodNow: Int? = null,
) {
    fun selectedPhotos(): List<String> = selected.mapNotNull { photos.getOrNull(it) }
}

@Serializable
data class LogbookEntry(
    val exerciseIndex: Int,
    val narrative: String = "",
    val takeaway: String = "",
)

@Serializable
data class LogbookContext(
    val summary: String = "",
    val whatHelped: List<String> = emptyList(),
    val howTheyFeelNow: String = "",
    val suggestionsNextSession: List<String> = emptyList(),
    val cautions: List<String> = emptyList(),
)

@Serializable
data class Logbook(
    val title: String,
    val opening: String = "",
    val entries: List<LogbookEntry> = emptyList(),
    val closing: String = "",
    val context: LogbookContext = LogbookContext(),
)

data class Visit(
    val id: String,
    val name: String,
    val lang: Lang,
    val maxExercises: Int,
    val mood: String,
    val previousContext: String? = null,
    val profilePhoto: String? = null,
    val startedAt: String,
    val endedAt: String? = null,
    val screen: Screen = Screen.CHECKIN,
    val checkin: CheckinReading? = null,
    val greeting: String? = null,
    /** Answers to the check-in questions. */
    val answers: List<CheckinAnswer> = emptyList(),
    val exercises: List<ExerciseRun> = emptyList(),
    val suggestion: Suggestion? = null,
    val logbook: Logbook? = null,
    val crisisText: String? = null,
) {
    val current: ExerciseRun? get() = exercises.lastOrNull()

    fun updateCurrent(f: (ExerciseRun) -> ExerciseRun): Visit =
        if (exercises.isEmpty()) this else copy(exercises = exercises.dropLast(1) + f(exercises.last()))
}
