package com.mentalcoach

import com.mentalcoach.data.api.CheckinResult
import com.mentalcoach.data.api.CoachRules
import com.mentalcoach.data.api.ExerciseRecord
import com.mentalcoach.data.api.LogbookResult
import com.mentalcoach.data.api.Prompts
import com.mentalcoach.data.api.ReflectResult
import com.mentalcoach.data.api.SessionPayload
import com.mentalcoach.data.api.StrataCoachApi
import com.mentalcoach.domain.ChatTurn
import com.mentalcoach.domain.CrisisDetector
import com.mentalcoach.domain.PhotoSelection
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSelectionTest {
    @Test
    fun defaultsToFirstAndLast() {
        assertEquals(emptyList<Int>(), PhotoSelection.default(0))
        assertEquals(listOf(0), PhotoSelection.default(1))
        assertEquals(listOf(0, 1), PhotoSelection.default(2))
        assertEquals(listOf(0, 4), PhotoSelection.default(5))
    }

    @Test
    fun toggleKeepsAtMostTwo() {
        assertEquals(listOf(0), PhotoSelection.toggle(listOf(0, 4), 4))
        assertEquals(listOf(0, 2), PhotoSelection.toggle(listOf(0), 2))
        // Adding a third drops the oldest pick.
        assertEquals(listOf(2, 3), PhotoSelection.toggle(listOf(0, 2), 3))
        assertEquals(listOf<Int>(), PhotoSelection.toggle(listOf(1), 1))
    }

    @Test
    fun removalShiftsIndices() {
        assertEquals(listOf(0, 2), PhotoSelection.afterRemoval(listOf(0, 3), 1))
        assertEquals(listOf(2), PhotoSelection.afterRemoval(listOf(0, 3), 0))
    }
}

class CoachContractTest {
    private val lib = TestLibrary.library
    private val json = StrataCoachApi.json

    private fun session(
        exercises: List<ExerciseRecord> = emptyList(),
        max: Int = 3,
        mood: String = "tired",
    ) = SessionPayload("Ana", "en", max, mood, exercises = exercises)

    @Test
    fun libraryContainsTheRequiredExercises() {
        listOf("box-breathing", "gentle-yoga", "push-ups").forEach { assertTrue(it, lib.find(it) != null) }
        lib.exercises.forEach {
            assertEquals(3, it.photoPrompts.en.size)
            assertEquals(it.steps.en.size, it.steps.de.size)
        }
    }

    @Test
    fun parsesCheckinWithFencesAndUnknownFields() {
        val raw =
            "```json\n{\"summary\":\"s\",\"feelings\":[\"a\",\"b\"],\"energy\":4,\"mood\":6,\"crisis\":false," +
                "\"greeting\":\"hi\",\"exerciseId\":\"push-ups\",\"exerciseIntro\":\"go\",\"extra\":1}\n```"
        val r = json.decodeFromString(CheckinResult.serializer(), StrataCoachApi.extractJson(raw))
        assertEquals("push-ups", r.exerciseId)
        assertEquals(4, r.energy)
    }

    @Test
    fun parsesReflectWithNullSuggestion() {
        val r = json.decodeFromString(ReflectResult.serializer(), """{"reply":"ok","crisis":false,"moodNow":null,"suggestion":null}""")
        assertNull(r.suggestion)
        assertNull(r.moodNow)
    }

    @Test
    fun parsesLogbook() {
        val r =
            json.decodeFromString(
                LogbookResult.serializer(),
                """{"title":"t","opening":"o","entries":[{"exerciseIndex":0,"narrative":"n","takeaway":"k"}],"closing":"c",
                   "context":{"summary":"s","whatHelped":["x"],"howTheyFeelNow":"h","suggestionsNextSession":[],"cautions":[]}}""",
            )
        assertEquals("k", r.entries.single().takeaway)
        assertEquals(listOf("x"), r.context.whatHelped)
    }

    @Test
    fun unknownExerciseIdFallsBackToTheLibrary() {
        val r = CoachRules.checkin(CheckinResult("s", exerciseId = "made-up", energy = 42), session(), lib)
        assertTrue(lib.find(r.exerciseId) != null)
        assertEquals(10, r.energy)

        val done = listOf(ExerciseRecord("box-breathing", "Box", chat = listOf(ChatTurn(Role.USER, "fine"))))
        val rr = CoachRules.reflect(ReflectResult("r", suggestion = Suggestion("next", "invented", "w")), session(done), lib)
        assertTrue(rr.suggestion!!.isNext)
        assertTrue(lib.find(rr.suggestion!!.exerciseId) != null)
        assertTrue(rr.suggestion!!.exerciseId != "box-breathing")
    }

    @Test
    fun nextBecomesEndWhenAllExercisesAreDone() {
        val done = listOf(ExerciseRecord("box-breathing", "Box"))
        val r = CoachRules.reflect(ReflectResult("r", suggestion = Suggestion("next", "push-ups", "w")), session(done, max = 1), lib)
        assertFalse(r.suggestion!!.isNext)
        assertNull(r.suggestion!!.exerciseId)
    }

    @Test
    fun keywordBackstopSetsCrisis() {
        val c = CoachRules.checkin(CheckinResult("s", greeting = "Welcome!"), session(mood = "Ich will mich umbringen"), lib)
        assertTrue(c.crisis)
        // The model's cheerful greeting is not reused as the crisis message.
        assertEquals("", c.greeting)
        val chat = listOf(ExerciseRecord("box-breathing", "Box", chat = listOf(ChatTurn(Role.USER, "I want to hurt myself"))))
        assertTrue(CoachRules.reflect(ReflectResult("r"), session(chat), lib).crisis)
        assertFalse(CrisisDetector.matches("I feel a bit low today"))
    }

    @Test
    fun promptsMentionTheLibraryAndTheSession() {
        val p = Prompts(lib)
        assertTrue(p.system.contains("- push-ups (3 min"))
        val text = p.checkin(session().copy(previousContext = "last time: walk helped"))
        assertTrue(text.startsWith("Visitor: Ana."))
        assertTrue(text.contains("<previous_context>"))
        assertTrue(text.contains("\"exerciseId\": one of"))
    }
}
