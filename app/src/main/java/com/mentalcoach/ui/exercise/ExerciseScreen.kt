package com.mentalcoach.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.domain.ExerciseNote
import com.mentalcoach.domain.ExerciseRun
import com.mentalcoach.domain.Lang
import com.mentalcoach.domain.PhotoSelection
import com.mentalcoach.ui.components.Eyebrow
import com.mentalcoach.ui.components.MicButton
import com.mentalcoach.ui.components.PhotoImage
import com.mentalcoach.ui.components.PreviewCamera
import com.mentalcoach.ui.components.PreviewData
import com.mentalcoach.ui.components.PrimaryButton
import com.mentalcoach.ui.components.ScreenColumn
import com.mentalcoach.ui.components.SecondaryButton
import com.mentalcoach.ui.components.SectionCard
import com.mentalcoach.ui.theme.MentalCoachTheme
import kotlinx.coroutines.delay
import java.time.Instant

/** Everything the exercise screen can do. Photos and notes are both optional. */
class ExerciseActions(
    val onShutter: () -> Unit = {},
    val onDeletePhoto: (Int) -> Unit = {},
    val onNoteDraft: (String) -> Unit = {},
    val onSaveNote: () -> Unit = {},
    val onDeleteNote: (Int) -> Unit = {},
    val onStep: (Int) -> Unit = {},
    val onEnd: () -> Unit = {},
    val onStopSpeaking: () -> Unit = {},
)

@Composable
fun ExerciseScreen(
    run: ExerciseRun,
    index: Int,
    max: Int,
    lang: Lang,
    noteDraft: String,
    cameraReady: Boolean,
    camera: @Composable (Modifier) -> Unit,
    actions: ExerciseActions,
) {
    val n = run.photos.size
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("${stringResource(R.string.exercise_of, index, max)} · ${stringResource(R.string.about_minutes, run.minutes)}")
                Text(run.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            Timer(run.startedAt, run.minutes)
        }
        BoxWithConstraints {
            val wide = maxWidth >= 700.dp
            val left =
                @Composable { m: Modifier ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (run.guided) GuidedStep(run, actions.onStep) else Steps(run.steps)
                        NoteInput(run, lang, noteDraft, actions)
                        Notes(run, actions.onDeleteNote)
                    }
                }
            val cam =
                @Composable { m: Modifier ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        camera(Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        run.photoPrompts.getOrNull(minOf(n, run.photoPrompts.size - 1))?.takeIf { !run.guided && n < PhotoSelection.MAX_PHOTOS }?.let {
                            Text(stringResource(R.string.photo_optional, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SecondaryButton(
                            stringResource(R.string.take_photo) + "  (${stringResource(R.string.photos_count, n)})",
                            actions.onShutter,
                            Modifier.fillMaxWidth(),
                            enabled = cameraReady && n < PhotoSelection.MAX_PHOTOS,
                        )
                        Thumbs(run.photos, actions.onDeletePhoto)
                    }
                }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    left(Modifier.weight(0.5f))
                    cam(Modifier.weight(0.5f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    left(Modifier)
                    cam(Modifier)
                }
            }
        }
        PrimaryButton(stringResource(R.string.end_exercise), actions.onEnd, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Steps(steps: List<String>) {
    SectionCard {
        steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
    }
}

/** One step at a time, read aloud by the coach; the visitor answers, then moves on. */
@Composable
private fun GuidedStep(
    run: ExerciseRun,
    onStep: (Int) -> Unit,
) {
    val i = run.stepIndex.coerceIn(0, run.steps.size - 1)
    SectionCard {
        Text(stringResource(R.string.step_of, i + 1, run.steps.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(run.steps[i], style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(stringResource(R.string.previous_step), { onStep(i - 1) }, Modifier.weight(1f), enabled = i > 0)
            SecondaryButton(stringResource(R.string.next_step), { onStep(i + 1) }, Modifier.weight(1f), enabled = i + 1 < run.steps.size)
        }
    }
}

@Composable
private fun NoteInput(
    run: ExerciseRun,
    lang: Lang,
    draft: String,
    actions: ExerciseActions,
) {
    Text(
        stringResource(if (run.guided) R.string.step_answer_label else R.string.note_label),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = actions.onNoteDraft,
            modifier = Modifier.weight(1f),
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onSaveNote() }),
        )
        MicButton(lang, draft, actions.onNoteDraft, actions.onStopSpeaking)
    }
    PrimaryButton(stringResource(R.string.save_note), actions.onSaveNote, Modifier.fillMaxWidth(), enabled = draft.isNotBlank())
}

@Composable
private fun Notes(
    run: ExerciseRun,
    onDelete: (Int) -> Unit,
) {
    if (run.notes.isEmpty()) return
    SectionCard {
        Text(stringResource(R.string.your_notes), style = MaterialTheme.typography.titleMedium)
        run.notes.forEachIndexed { i, note ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    note.step?.let { s ->
                        run.steps.getOrNull(s)?.let {
                            Text(stringResource(R.string.step_of, s + 1, run.steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(note.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
                }
                IconButton(onClick = { onDelete(i) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.delete_note))
                }
            }
        }
    }
}

@Composable
private fun Timer(
    startedAt: String?,
    minutes: Int,
) {
    val start = remember(startedAt) { runCatching { Instant.parse(startedAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val sec = ((now - start) / 1000).coerceAtLeast(0)
    val over = sec >= minutes * 60
    Text(
        "%d:%02d".format(sec / 60, sec % 60),
        style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
        color = if (over) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun Thumbs(
    photos: List<String>,
    onDelete: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PhotoSelection.MAX_PHOTOS) { i ->
            Box(Modifier.weight(1f).aspectRatio(1f)) {
                val path = photos.getOrNull(i)
                if (path == null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant))
                } else {
                    PhotoImage(path, Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
                    val desc = stringResource(R.string.delete_photo)
                    FilledIconButton(
                        onClick = { onDelete(i) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(40.dp).semantics { contentDescription = desc },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    ) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

private val guidedPreview =
    PreviewData.run.copy(
        title = "5-4-3-2-1 grounding",
        guided = true,
        stepIndex = 1,
        steps = listOf("Name five things you can see.", "Notice four things you can touch.", "Listen for three things you can hear."),
        notes = listOf(ExerciseNote("A plant, the window, my shoes, a lamp, a cup", 0)),
    )

@Preview(name = "Exercise EN", widthDp = 1100, heightDp = 900, showBackground = true)
@Composable
private fun ExercisePreviewEn() =
    MentalCoachTheme { ExerciseScreen(PreviewData.run, 1, 3, Lang.EN, "", false, { PreviewCamera(it) }, ExerciseActions()) }

@Preview(name = "Exercise guided DE", widthDp = 400, heightDp = 1200, showBackground = true, locale = "de")
@Composable
private fun ExercisePreviewDe() =
    MentalCoachTheme { ExerciseScreen(guidedPreview, 1, 3, Lang.DE, "Die Tischplatte", false, { PreviewCamera(it) }, ExerciseActions()) }
