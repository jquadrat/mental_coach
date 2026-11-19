package com.mentalcoach.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.domain.CheckinQuestion
import com.mentalcoach.domain.Lang
import com.mentalcoach.ui.components.Eyebrow
import com.mentalcoach.ui.components.MicButton
import com.mentalcoach.ui.components.PrimaryButton
import com.mentalcoach.ui.components.ScreenColumn
import com.mentalcoach.ui.components.SecondaryButton
import com.mentalcoach.ui.theme.MentalCoachTheme

class QuestionActions(
    val onPick: (CheckinQuestion, Int) -> Unit = { _, _ -> },
    val onSpoken: (CheckinQuestion, String) -> Unit = { _, _ -> },
    val onSpokenDone: (CheckinQuestion, String) -> Unit = { _, _ -> },
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onStopSpeaking: () -> Unit = {},
)

fun CheckinQuestion.titleRes() =
    when (this) {
        CheckinQuestion.FEEL_MORE -> R.string.q_feelMore
        CheckinQuestion.LEAVE_BEHIND -> R.string.q_leaveBehind
        CheckinQuestion.INTEREST -> R.string.q_interest
        CheckinQuestion.TIME -> R.string.q_time
        CheckinQuestion.STATE -> R.string.q_state
    }

fun CheckinQuestion.optionsRes() =
    when (this) {
        CheckinQuestion.FEEL_MORE -> R.array.q_feelMore_options
        CheckinQuestion.LEAVE_BEHIND -> R.array.q_leaveBehind_options
        CheckinQuestion.INTEREST -> R.array.q_interest_options
        CheckinQuestion.TIME -> R.array.q_time_options
        CheckinQuestion.STATE -> R.array.q_state_options
    }

/** One check-in question: options to tap, or an answer by speech that moves on by itself. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckinQuestionStep(
    question: CheckinQuestion,
    picks: Set<Int>,
    spoken: String,
    lang: Lang,
    actions: QuestionActions,
) {
    val step = question.ordinal
    val total = CheckinQuestion.entries.size
    ScreenColumn {
        Eyebrow(stringResource(R.string.question_of, step + 1, total))
        LinearProgressIndicator(progress = { (step + 1f) / total }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(question.titleRes()), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.question_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stringArrayResource(question.optionsRes()).forEachIndexed { i, label ->
                val on = i in picks
                FilterChip(
                    selected = on,
                    onClick = { actions.onPick(question, i) },
                    label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = if (on) ({ Icon(Icons.Default.Check, null) }) else null,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MicButton(
                lang = lang,
                current = "",
                onText = { actions.onSpoken(question, it) },
                onStart = actions.onStopSpeaking,
                onFinished = { actions.onSpokenDone(question, it) },
            )
            if (spoken.isNotBlank()) {
                Text(stringResource(R.string.you_said, spoken), fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(stringResource(R.string.back), actions.onBack, Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.continue_button), actions.onNext, Modifier.weight(1f))
        }
    }
}

@Preview(name = "Question EN", widthDp = 900, heightDp = 700, showBackground = true)
@Composable
private fun QuestionPreviewEn() =
    MentalCoachTheme { CheckinQuestionStep(CheckinQuestion.FEEL_MORE, setOf(0, 4), "calmer and a bit more hopeful", Lang.EN, QuestionActions()) }

@Preview(name = "Question DE", widthDp = 400, heightDp = 900, showBackground = true, locale = "de")
@Composable
private fun QuestionPreviewDe() = MentalCoachTheme { CheckinQuestionStep(CheckinQuestion.STATE, setOf(1), "", Lang.DE, QuestionActions()) }
