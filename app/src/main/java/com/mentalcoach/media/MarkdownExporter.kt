package com.mentalcoach.media

import com.mentalcoach.data.api.Prompts
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Visit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Builds the context .md for the next visit. Mirrors contextMarkdown() in mentalcoach/public/app.js. No photos. */
class MarkdownExporter(
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    private fun time(iso: String?) = iso?.let { runCatching { hhmm.format(Instant.parse(it)) }.getOrNull() } ?: "?"

    private fun minutes(
        a: String?,
        b: String?,
    ): String =
        runCatching {
            maxOf(1L, Math.round(Duration.between(Instant.parse(a), Instant.parse(b)).toMillis() / 60000.0)).toString()
        }.getOrDefault("?")

    private fun list(xs: List<String>) = if (xs.isEmpty()) "- (none)" else xs.joinToString("\n") { "- $it" }

    fun export(v: Visit): String {
        val lb = requireNotNull(v.logbook) { "Logbook missing" }
        val c = lb.context
        val date =
            runCatching { DateTimeFormatter.ISO_LOCAL_DATE.withZone(zone).format(Instant.parse(v.startedAt)) }
                .getOrDefault(v.startedAt.take(10))
        val lines =
            mutableListOf(
                "# MentalCoach context notes",
                "",
                "- **Visitor:** ${v.name}",
                "- **Date:** $date (${time(v.startedAt)}–${time(v.endedAt)})",
                "- **Language:** ${v.lang.code}",
                "- **Exercises:** ${v.exercises.size} done of ${v.maxExercises} planned",
                "- **Logbook title:** ${lb.title}",
                "",
                "## Summary",
                "",
                c.summary,
                "",
                "## How they feel now",
                "",
                c.howTheyFeelNow,
                "",
                "## What helped",
                "",
                list(c.whatHelped),
                "",
                "## Suggestions for the next session",
                "",
                list(c.suggestionsNextSession),
                "",
            )
        if (c.cautions.isNotEmpty()) lines += listOf("## Cautions", "", list(c.cautions), "")
        lines += listOf("## Check-in", "", "> " + v.mood.replace("\n", "\n> "), "")
        v.checkin?.let {
            lines += "Coach reading: ${it.summary} Mood ${it.mood}/10, energy ${it.energy}/10. Feelings: ${it.feelings.joinToString(", ")}."
            lines += ""
        }
        if (v.answers.isNotEmpty()) {
            lines += "Check-in questions:"
            lines += ""
            v.answers.forEach { lines += "- ${Prompts.QUESTION_TEXT[it.question] ?: it.question}: ${Prompts.answerText(it)}" }
            lines += ""
        }
        lines += listOf("## Exercises", "")
        v.exercises.forEachIndexed { i, ex ->
            lines += "### ${i + 1}. ${ex.title} (`${ex.exerciseId}`)"
            lines += ""
            lines += "- Time: ${time(ex.startedAt)}–${time(ex.endedAt)} (${minutes(ex.startedAt, ex.endedAt)} min)"
            lines += "- Photos: ${ex.photos.size} taken, ${ex.selected.size} kept in the logbook (photos stay with the visitor)"
            ex.moodNow?.let { lines += "- Mood afterwards: $it/10" }
            if (ex.notes.isNotEmpty()) {
                lines += listOf("", "Said or written during the exercise:", "")
                ex.notes.forEach { n ->
                    lines += "- " + (n.step?.let { "(step ${it + 1}) " } ?: "") + n.text.replace(Regex("\n+"), " ")
                }
            }
            lines += listOf("", "Conversation afterwards:", "")
            ex.chat.forEach { m ->
                lines += "- **${if (m.role == Role.COACH) "Coach" else v.name}:** ${m.text.replace(Regex("\n+"), " ")}"
            }
            lines += ""
        }
        return lines.joinToString("\n")
    }
}
