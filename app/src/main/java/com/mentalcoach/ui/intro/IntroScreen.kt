package com.mentalcoach.ui.intro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.domain.CheckinReading
import com.mentalcoach.domain.ExerciseRun
import com.mentalcoach.ui.components.Eyebrow
import com.mentalcoach.ui.components.PreviewData
import com.mentalcoach.ui.components.PrimaryButton
import com.mentalcoach.ui.components.ScreenColumn
import com.mentalcoach.ui.components.SecondaryButton
import com.mentalcoach.ui.components.SectionCard
import com.mentalcoach.ui.theme.MentalCoachTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntroScreen(
    reading: CheckinReading?,
    run: ExerciseRun,
    index: Int,
    max: Int,
    onRepeat: () -> Unit,
    onStart: () -> Unit,
    onEndVisit: () -> Unit,
) {
    ScreenColumn {
        if (reading != null && index == 1) {
            SectionCard {
                Text(reading.summary, style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reading.feelings.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
                Meter(stringResource(R.string.mood_meter), reading.mood)
                Meter(stringResource(R.string.energy_meter), reading.energy)
            }
        }
        Eyebrow("${stringResource(R.string.exercise_of, index, max)} · ${stringResource(R.string.about_minutes, run.minutes)}")
        Text(run.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(run.intro, style = MaterialTheme.typography.bodyLarge)
        SectionCard {
            Text(stringResource(R.string.steps), style = MaterialTheme.typography.titleMedium)
            run.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
        }
        SectionCard {
            Text(stringResource(R.string.intro_photos), style = MaterialTheme.typography.titleMedium)
            run.photoPrompts.forEach { Text("• $it") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(stringResource(R.string.repeat_instructions), onRepeat, Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.start_exercise), onStart, Modifier.weight(1f))
        }
        TextButton(onClick = onEndVisit, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.end_visit_now))
        }
    }
}

@Composable
private fun Meter(
    label: String,
    value: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.width(96.dp))
        LinearProgressIndicator(
            progress = { value / 10f },
            modifier = Modifier.weight(1f).height(10.dp),
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(stringResource(R.string.score_of_ten, value), modifier = Modifier.width(48.dp))
    }
}

@Preview(name = "Intro EN", widthDp = 900, heightDp = 1000, showBackground = true)
@Composable
private fun IntroPreviewEn() =
    MentalCoachTheme { IntroScreen(PreviewData.visit.checkin, PreviewData.run, 1, 3, {}, {}, {}) }

@Preview(name = "Intro DE", widthDp = 400, heightDp = 900, showBackground = true, locale = "de")
@Composable
private fun IntroPreviewDe() =
    MentalCoachTheme { IntroScreen(PreviewData.visit.checkin, PreviewData.run, 1, 3, {}, {}, {}) }
