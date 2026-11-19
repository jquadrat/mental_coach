package com.mentalcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.mentalcoach.domain.ChatTurn
import com.mentalcoach.domain.CheckinReading
import com.mentalcoach.domain.ExerciseRun
import com.mentalcoach.domain.Lang
import com.mentalcoach.domain.Logbook
import com.mentalcoach.domain.LogbookEntry
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Screen
import com.mentalcoach.domain.Suggestion
import com.mentalcoach.domain.Visit

/** Sample data for @Preview only. */
object PreviewData {
    val run =
        ExerciseRun(
            exerciseId = "box-breathing",
            title = "Box breathing",
            intro = "Let's start with box breathing. It calms the body quickly.",
            minutes = 4,
            steps = listOf("Sit comfortably.", "Breathe in for four counts.", "Hold for four counts.", "Breathe out for four counts."),
            photoPrompts = listOf("The spot where you sit", "Your hands", "Your face after the last breath"),
            startedAt = "2026-11-19T10:00:00Z",
            endedAt = "2026-11-19T10:05:00Z",
            chat =
                listOf(
                    ChatTurn(Role.COACH, "Welcome back, Ana. How do you feel now, after the exercise?"),
                    ChatTurn(Role.USER, "Calmer. My shoulders dropped."),
                    ChatTurn(Role.COACH, "That sounds like a good shift. As a next step I'd suggest a mindful walk."),
                ),
        )

    val visit =
        Visit(
            id = "preview",
            name = "Ana",
            lang = Lang.EN,
            maxExercises = 3,
            mood = "A bit stressed after a long week, I can't switch off.",
            startedAt = "2026-11-19T10:00:00Z",
            screen = Screen.INTRO,
            checkin = CheckinReading("It sounds like a lot is on your mind.", listOf("tense", "tired", "hopeful"), energy = 4, mood = 5),
            greeting = "Welcome, Ana.",
            exercises = listOf(run),
            suggestion = Suggestion(Suggestion.ACTION_NEXT, "mindful-walk", "A mindful walk can build on that calm."),
            logbook =
                Logbook(
                    title = "A day to breathe",
                    opening = "You came to the lab today feeling stressed after a long week.",
                    entries = listOf(LogbookEntry(0, "You took four calm minutes to breathe in a square.", "Four counts in, four counts out.")),
                    closing = "You leave with a calmer breath and a tool for home.",
                ),
        )
}

@Composable
fun PreviewCamera(modifier: Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant))
}
