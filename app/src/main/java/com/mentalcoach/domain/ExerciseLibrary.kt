package com.mentalcoach.domain

import kotlinx.serialization.json.Json

/** The bundled exercise library. The coach may only choose from these. */
class ExerciseLibrary(
    val exercises: List<Exercise>,
) {
    init {
        require(exercises.isNotEmpty()) { "Exercise library is empty" }
    }

    fun find(id: String?): Exercise? = exercises.firstOrNull { it.id == id }

    /** Returns the exercise for [id], or a valid fallback that was not used yet. */
    fun resolve(
        id: String?,
        used: Collection<String> = emptyList(),
    ): Exercise = find(id) ?: exercises.firstOrNull { it.id !in used } ?: exercises.first()

    fun catalogueText(): String =
        exercises.joinToString("\n") { "- ${it.id} (${it.minutes} min, \"${it.title.en}\"): ${it.goodFor}" }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ExerciseLibrary = ExerciseLibrary(json.decodeFromString(text))
    }
}
