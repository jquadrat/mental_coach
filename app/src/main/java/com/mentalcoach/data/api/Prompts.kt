package com.mentalcoach.data.api

import com.mentalcoach.domain.ExerciseLibrary
import com.mentalcoach.domain.Lang
import com.mentalcoach.domain.Role

/** Prompts ported from mentalcoach/server/coach.ts. Built on the device. */
class Prompts(
    private val library: ExerciseLibrary,
) {
    val system: String by lazy {
        """
        You are a warm, calm coaching companion in an on-site wellbeing lab. A visitor checks in, describes how they feel, does a few short exercises while taking photos, and talks with you after each one. Everything you write in "greeting", "exerciseIntro", "reply" and "why" is spoken aloud by speech synthesis, so write plain, short spoken sentences without lists, markdown, emojis or parentheses.

        You are not a therapist and do not diagnose. Stay supportive, concrete and brief. Mirror the visitor's words, ask at most one question at a time, and never pressure them.

        Choose exercises only from this catalogue, by id:
        CATALOGUE

        Safety: if the visitor mentions wanting to harm themselves or others, suicidal thoughts, or being in acute danger, set "crisis" to true, respond with care, and gently encourage them to talk to the lab staff or a crisis line right now. Do not continue with exercises in that case.

        Always answer in the visitor's chosen language.

        Output format: reply with exactly one JSON object that matches the requested shape. No markdown, no code fences, no text before or after the JSON.
        """.trimIndent().replace("CATALOGUE", library.catalogueText())
    }

    private val ids get() = library.exercises.joinToString(" | ") { "\"${it.id}\"" }

    /** Exercise names to use when speaking, in the visit language. */
    private fun names(s: SessionPayload): String {
        val lang = Lang.from(s.lang)
        return "When you mention an exercise, call it by its name in the visitor's language: " +
            library.exercises.joinToString("; ") { "${it.id} = \"${it.title[lang]}\"" } + "."
    }

    fun checkin(s: SessionPayload): String =
        describe(s) + "\n\n" +
            """
        Read the check-in. Estimate mood and energy, name the feelings, welcome the visitor and choose the first exercise that best fits how they feel right now.
        NAMES

        Return JSON with this shape:
        {
          "summary": string,        // 1-2 warm sentences, second person, reflecting back how the person feels
          "feelings": string[],     // 2-4 single-word feelings you recognise
          "energy": integer,        // 1 (exhausted) to 10 (very energised)
          "mood": integer,          // 1 (very low) to 10 (very good)
          "crisis": boolean,        // true only if there are signs of acute danger to self or others
          "greeting": string,       // spoken welcome, 2-3 short sentences, uses the person's first name
          "exerciseId": one of $ids,
          "exerciseIntro": string   // spoken: why this exercise fits now and how to begin, 2-4 short sentences
        }
        """.trimIndent().replace("NAMES", names(s))

    fun reflect(s: SessionPayload): String {
        val current = s.exercises.lastOrNull()
        val userTurns = current?.chat?.count { it.role == Role.USER } ?: 0
        val done = s.exercises.joinToString(", ") { it.id }.ifEmpty { "none" }
        val remaining = s.maxExercises - s.exercises.size
        val plan =
            if (remaining > 0) {
                "Propose a follow-up (\"next\") that builds on how they feel now and is not one they already did, unless they say they want to stop, then propose \"end\"."
            } else {
                "The planned exercises are done, so propose \"end\"."
            }
        return describe(s) + "\n\n" +
            """
            You are talking with the visitor after exercise ${s.exercises.size} about how they feel now. Reply to their latest message. The visitor has answered $userTurns time(s) in this conversation.
            - If they have answered fewer than 2 times and still seem to want to talk, keep listening (suggestion null) and ask one gentle question.
            - Otherwise make a suggestion. Exercises already done: $done. Exercises left in this visit: $remaining. $plan
            - If the visitor explicitly asks for a different exercise or to stop, follow their wish.
            NAMES

            Return JSON with this shape:
            {
              "reply": string,          // spoken coach reply, 1-3 short sentences
              "crisis": boolean,
              "moodNow": integer | null,  // mood 1-10 after the exercise if you can tell, else null
              "suggestion": null | {    // null while you still want to hear more
                "action": "next" | "end",
                "exerciseId": one of $ids, or null when action is "end",
                "why": string           // spoken, 1-2 sentences: why this follow-up, or why it is a good moment to finish
              }
            }
            """.trimIndent().replace("NAMES", names(s))
    }

    fun logbook(s: SessionPayload): String =
        describe(s) + "\n\n" +
            """
        The visit is over. Write the take-home logbook for the visitor in ${langName(s.lang)}: a personal memory of the experience in the second person, one entry per exercise (exerciseIndex starts at 0), plus a factual context block in English for the coach of the next session. Use only what actually happened in the conversation and in the visitor's notes during the exercises, do not invent details. Where the visitor recorded notes, weave their own words into that exercise's narrative.

        Return JSON with this shape:
        {
          "title": string,          // a short, personal title for the day, max 8 words
          "opening": string,        // 2-3 sentences: how the person arrived (second person)
          "entries": [ { "exerciseIndex": integer, "narrative": string, "takeaway": string } ],
                                    // narrative: 3-4 sentences, what they did and noticed, as a memory; takeaway: one short sentence to remember at home
          "closing": string,        // 2-3 sentences: how they leave and an encouraging thought
          "context": {
            "summary": string,
            "whatHelped": string[],
            "howTheyFeelNow": string,
            "suggestionsNextSession": string[],
            "cautions": string[]    // anything a future coach should be careful about; empty if none
          }
        }
        """.trimIndent()

    companion object {
        fun langName(lang: String) = if (lang == "de") "German (use informal du)" else "English"

        fun describe(s: SessionPayload): String {
            val parts = mutableListOf<String>()
            parts += "Visitor: ${s.name}. Language: ${langName(s.lang)}. Planned exercises this visit: ${s.maxExercises}."
            s.previousContext?.takeIf { it.isNotBlank() }?.let {
                parts += "Notes from a previous visit:\n<previous_context>\n${it.take(6000)}\n</previous_context>"
            }
            parts += "Check-in, in the visitor's words:\n<checkin>\n${s.mood}\n</checkin>"
            s.checkin?.let {
                parts += "Your check-in reading: ${it.summary} (mood ${it.mood}/10, energy ${it.energy}/10)."
            }
            s.exercises.forEachIndexed { i, ex ->
                val notes =
                    ex.notes.joinToString("\n") { n -> "- " + (n.step?.let { "(step ${it + 1}) " } ?: "") + n.text }
                val chat =
                    ex.chat.joinToString("\n") { t -> "${if (t.role == Role.COACH) "Coach" else "Visitor"}: ${t.text}" }
                parts += "Exercise ${i + 1}: ${ex.title} (${ex.id})." +
                    (if (notes.isNotEmpty()) "\nWhat the visitor said or wrote during the exercise:\n$notes" else "") +
                    (if (chat.isNotEmpty()) "\nConversation afterwards:\n$chat" else "")
            }
            return parts.joinToString("\n\n")
        }
    }
}
