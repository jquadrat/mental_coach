package com.mentalcoach.data.api

import com.mentalcoach.domain.ExerciseLibrary
import com.mentalcoach.domain.Lang
import com.mentalcoach.domain.LogbookContext
import com.mentalcoach.domain.LogbookEntry
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Suggestion
import kotlinx.coroutines.delay

/**
 * Scripted coach used in demo mode and in tests (ported from mentalcoach/server/demo.ts).
 * Lets the lab try the full flow offline.
 */
class FakeCoachApi(
    private val library: ExerciseLibrary,
    private val latencyMs: Long = 0,
) : CoachApi {
    /** Test hooks: calls made, and an optional error to throw on the next call. */
    var calls = mutableListOf<String>()
    var failNext: Throwable? = null

    private val keywords =
        listOf(
            "box-breathing" to listOf("stress", "nervous", "anxious", "panic", "gestresst", "nervös", "angst", "unruhig"),
            "grounding-54321" to listOf("overwhelm", "dizzy", "scattered", "überfordert", "durcheinander"),
            "gratitude-three" to listOf("sad", "down", "low", "depress", "traurig", "niedergeschlagen", "schlecht"),
            "gentle-yoga" to listOf("tense", "stiff", "verspannt", "steif"),
            "body-scan" to listOf("tired", "exhausted", "pain", "müde", "erschöpft"),
            "push-ups" to listOf("angry", "frustrat", "wütend", "frust"),
            "kind-letter" to listOf("guilt", "ashamed", "failed", "stupid", "schuld", "versagt", "schäme"),
            "power-posture" to listOf("insecure", "doubt", "small", "unsicher", "zweifel"),
            "mindful-walk" to listOf("bored", "stuck", "restless", "gelangweilt", "festgefahren"),
        )
    private val order =
        listOf("gentle-yoga", "grounding-54321", "push-ups", "gratitude-three", "power-posture", "mindful-walk", "body-scan", "box-breathing")

    private suspend fun step(name: String) {
        calls += name
        if (latencyMs > 0) delay(latencyMs)
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun checkin(session: SessionPayload): CheckinResult {
        step("checkin")
        val de = session.lang == Lang.DE.code
        val t = session.mood.lowercase()
        val id = keywords.firstOrNull { (_, ws) -> ws.any { t.contains(it) } }?.first ?: "box-breathing"
        val title = library.resolve(id).title[Lang.from(session.lang)]
        val n = session.name
        val r =
            CheckinResult(
                summary =
                    if (de) {
                        "Danke, dass du das teilst, $n. Es klingt, als ginge dir heute einiges durch den Kopf."
                    } else {
                        "Thank you for sharing, $n. It sounds like there is quite a lot on your mind today."
                    },
                feelings = if (de) listOf("angespannt", "nachdenklich") else listOf("tense", "thoughtful"),
                energy = 5,
                mood = 5,
                crisis = false,
                greeting =
                    if (de) {
                        "Willkommen, $n. Schön, dass du da bist. Wir gehen das Schritt für Schritt an."
                    } else {
                        "Welcome, $n. I'm glad you're here. Let's take this one step at a time."
                    },
                exerciseId = id,
                exerciseIntro =
                    if (de) {
                        "Lass uns mit $title beginnen. Das hilft dir, anzukommen und zu spüren, wie es dir geht. Ich zeige dir die Schritte, und du machst dabei ein paar Fotos."
                    } else {
                        "Let's start with $title. It helps to settle in and notice how you feel. I'll show you the steps, and you can take a few photos along the way."
                    },
            )
        return CoachRules.checkin(r, session, library)
    }

    override suspend fun reflect(session: SessionPayload): ReflectResult {
        step("reflect")
        val de = session.lang == Lang.DE.code
        val lang = Lang.from(session.lang)
        val current = session.exercises.last()
        val userTurns = current.chat.count { it.role == Role.USER }
        val r =
            if (userTurns < 2) {
                ReflectResult(
                    reply =
                        if (de) {
                            "Danke. Was hast du während der Übung in deinem Körper oder in deinen Gedanken bemerkt?"
                        } else {
                            "Thank you. What did you notice in your body or your thoughts during the exercise?"
                        },
                )
            } else {
                val wantsStop =
                    Regex("stop|end|enough|aufhören|beenden|genug", RegexOption.IGNORE_CASE)
                        .containsMatchIn(current.chat.last().text)
                val used = session.exercises.map { it.id }.toSet()
                val nextId =
                    if (session.exercises.size < session.maxExercises && !wantsStop) order.firstOrNull { it !in used } else null
                if (nextId != null) {
                    val title = library.resolve(nextId).title[lang]
                    val why =
                        if (de) {
                            "Das klingt nach einer guten Veränderung. Als Nächstes schlage ich $title vor, um darauf aufzubauen."
                        } else {
                            "That sounds like a good shift. As a next step I'd suggest $title, to build on that."
                        }
                    ReflectResult(why, moodNow = 6, suggestion = Suggestion(Suggestion.ACTION_NEXT, nextId, why))
                } else {
                    val end =
                        if (de) {
                            "Du hast heute viel gemacht. Das ist ein guter Moment, um aufzuhören und dein Logbuch zu erstellen."
                        } else {
                            "You did a lot today. This feels like a good moment to finish and put together your logbook."
                        }
                    ReflectResult(end, moodNow = 7, suggestion = Suggestion(Suggestion.ACTION_END, null, end))
                }
            }
        return CoachRules.reflect(r, session, library)
    }

    override suspend fun logbook(session: SessionPayload): LogbookResult {
        step("logbook")
        val de = session.lang == Lang.DE.code
        val lang = Lang.from(session.lang)
        val mood = session.mood.take(160).trimEnd('.', '!', '?')
        return CoachRules.logbook(
            LogbookResult(
            title = if (de) "Ein Tag zum Durchatmen" else "A day to breathe",
            opening =
                if (de) {
                    "Du bist heute ins Lab gekommen und hast erzählt: „$mood“. Das war ein mutiger erster Schritt."
                } else {
                    "You came to the lab today and said: \"$mood\". That was a brave first step."
                },
            entries =
                session.exercises.mapIndexed { i, ex ->
                    val said =
                        ex.chat
                            .filter { it.role == Role.USER }
                            .joinToString(". ") { it.text.trim().trimEnd('.', '!', '?') }
                            .take(200)
                    val title = library.find(ex.id)?.title?.get(lang) ?: ex.title
                    LogbookEntry(
                        exerciseIndex = i,
                        narrative =
                            if (de) "Du hast dir Zeit für $title genommen. Danach hast du erzählt: „$said“." else "You took time for $title. Afterwards you said: \"$said\".",
                        takeaway = if (de) "Ein paar Minuten für dich machen einen Unterschied." else "A few minutes for yourself make a difference.",
                    )
                },
            closing =
                if (de) {
                    "Du gehst heute mit ein paar neuen Werkzeugen nach Hause. Nimm dir dieses Logbuch, wann immer du es brauchst."
                } else {
                    "You leave today with a few new tools. Come back to this logbook whenever you need it."
                },
            context =
                LogbookContext(
                    summary = "(Demo mode) ${session.name} checked in and completed ${session.exercises.size} exercise(s): ${session.exercises.joinToString(", ") { it.id }}.",
                    whatHelped = session.exercises.map { it.title },
                    howTheyFeelNow = "Not analysed in demo mode; see the conversation notes.",
                    suggestionsNextSession =
                        listOf(
                            "Try ${library.exercises.firstOrNull { e -> session.exercises.none { it.id == e.id } }?.id ?: "a repeat of a favourite exercise"}.",
                        ),
                    cautions = emptyList(),
                ),
            ),
            session,
        )
    }
}
