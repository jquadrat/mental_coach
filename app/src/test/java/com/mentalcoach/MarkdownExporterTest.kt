package com.mentalcoach

import com.mentalcoach.domain.ChatTurn
import com.mentalcoach.domain.CheckinReading
import com.mentalcoach.domain.ExerciseRun
import com.mentalcoach.domain.Lang
import com.mentalcoach.domain.Logbook
import com.mentalcoach.domain.LogbookContext
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Screen
import com.mentalcoach.domain.Visit
import com.mentalcoach.media.MarkdownExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.ZoneOffset

class MarkdownExporterTest {
    private val visit =
        Visit(
            id = "v1",
            name = "Ana",
            lang = Lang.EN,
            maxExercises = 3,
            mood = "Stressed after a long week.\nI can't switch off.",
            startedAt = "2026-11-19T10:00:00Z",
            endedAt = "2026-11-19T10:40:00Z",
            screen = Screen.LOGBOOK,
            checkin = CheckinReading("A lot is on your mind.", listOf("tense", "tired"), energy = 4, mood = 5),
            exercises =
                listOf(
                    ExerciseRun(
                        exerciseId = "box-breathing",
                        title = "Box breathing",
                        intro = "",
                        minutes = 4,
                        steps = emptyList(),
                        photoPrompts = emptyList(),
                        startedAt = "2026-11-19T10:05:00Z",
                        endedAt = "2026-11-19T10:10:20Z",
                        photos = listOf("/p/a.jpg", "/p/b.jpg", "/p/c.jpg"),
                        selected = listOf(0, 2),
                        chat =
                            listOf(
                                ChatTurn(Role.COACH, "How do you feel now?"),
                                ChatTurn(Role.USER, "Calmer.\n\nMy shoulders dropped."),
                            ),
                        moodNow = 7,
                    ),
                ),
            logbook =
                Logbook(
                    title = "A day to breathe",
                    context =
                        LogbookContext(
                            summary = "Ana arrived stressed and did box breathing.",
                            whatHelped = listOf("Slow breathing"),
                            howTheyFeelNow = "Calmer.",
                            suggestionsNextSession = emptyList(),
                            cautions = listOf("Mentioned poor sleep."),
                        ),
                ),
        )

    @Test
    fun matchesGoldenFile() {
        val expected = javaClass.getResource("/golden/context.md")!!.readText()
        assertEquals(expected, MarkdownExporter(ZoneOffset.UTC).export(visit))
    }

    @Test
    fun neverContainsPhotoPaths() {
        assertFalse(MarkdownExporter(ZoneOffset.UTC).export(visit).contains(".jpg"))
    }
}
