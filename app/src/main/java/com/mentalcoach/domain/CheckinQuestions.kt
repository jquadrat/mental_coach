package com.mentalcoach.domain

import kotlinx.serialization.Serializable

/** The check-in questions asked after the form, one at a time. Labels live in string resources. */
enum class CheckinQuestion(
    val id: String,
    val multi: Boolean,
) {
    FEEL_MORE("feelMore", multi = true),
    LEAVE_BEHIND("leaveBehind", multi = true),
    INTEREST("interest", multi = true),
    TIME("time", multi = false),
    STATE("state", multi = false),
}

/** One answered question, with labels in the visit language, as sent to the coach. */
@Serializable
data class CheckinAnswer(
    val question: String,
    val selected: List<String> = emptyList(),
    val spoken: String = "",
)

object CheckinAnswers {
    /** Exercises per visit for the TIME options (30, 60, 90, >90 minutes); null = keep the setting. */
    fun exercisesForTime(option: Int?): Int? = listOf(2, 3, 4, 5).getOrNull(option ?: -1)

    /** Options whose significant words appear in what the visitor said. */
    fun match(
        spoken: String,
        options: List<String>,
    ): Set<Int> = scores(spoken, options).filterValues { it > 0 }.keys

    /** For single-choice questions: the option with the most words the visitor said, if any. */
    fun best(
        spoken: String,
        options: List<String>,
    ): Int? = scores(spoken, options).filterValues { it > 0 }.maxByOrNull { it.value }?.key

    private fun scores(
        spoken: String,
        options: List<String>,
    ): Map<Int, Int> {
        val said = spoken.lowercase()
        val saidWords = said.split(Regex("[^\\p{L}0-9]+")).toSet()
        return options
            .mapIndexed { i, label ->
                val words =
                    label
                        .lowercase()
                        .split(Regex("[^\\p{L}0-9]+"))
                        .filter { w -> (w.length >= 4 || w.all(Char::isDigit)) && w.isNotEmpty() && w !in STOP_WORDS }
                i to
                    words.count { w ->
                        // Numbers must match exactly; words may differ in their ending ("grübel" ~ "grübeln").
                        if (w.all(Char::isDigit)) w in saidWords else said.contains(w.dropLast(minOf(2, w.length - 4)))
                    }
            }.toMap()
    }

    // Words too generic to identify an option on their own.
    private val STOP_WORDS = setOf("mich", "meine", "than", "more", "with", "mit", "minuten", "minutes", "selbst", "myself", "anderen", "other", "people")
}
