package com.mentalcoach.ui.exercise

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.domain.ExerciseRun
import com.mentalcoach.domain.PhotoSelection
import com.mentalcoach.ui.components.ConfirmDialog
import com.mentalcoach.ui.components.Eyebrow
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

@Composable
fun ExerciseScreen(
    run: ExerciseRun,
    index: Int,
    max: Int,
    cameraReady: Boolean,
    camera: @Composable (Modifier) -> Unit,
    onShutter: () -> Unit,
    onDeletePhoto: (Int) -> Unit,
    onEnd: () -> Unit,
) {
    var confirmNoPhotos by remember { mutableStateOf(false) }
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
            val steps =
                @Composable { m: Modifier ->
                    SectionCard(m) {
                        run.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            val cam =
                @Composable { m: Modifier ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        camera(Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        val prompt =
                            when {
                                n < run.photoPrompts.size -> "${stringResource(R.string.photo_of, n + 1)} ${run.photoPrompts[n]}"
                                n < PhotoSelection.MAX_PHOTOS -> stringResource(R.string.photo_extra)
                                else -> stringResource(R.string.all_photos)
                            }
                        Text(prompt, style = MaterialTheme.typography.titleMedium)
                        PrimaryButton(
                            stringResource(R.string.take_photo) + "  (${stringResource(R.string.photos_count, n)})",
                            onShutter,
                            Modifier.fillMaxWidth(),
                            enabled = cameraReady && n < PhotoSelection.MAX_PHOTOS,
                        )
                        Thumbs(run.photos, onDeletePhoto)
                    }
                }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    cam(Modifier.weight(0.55f))
                    steps(Modifier.weight(0.45f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    steps(Modifier)
                    cam(Modifier)
                }
            }
        }
        val end = { if (n == 0) confirmNoPhotos = true else onEnd() }
        if (n >= run.photoPrompts.size) {
            PrimaryButton(stringResource(R.string.end_exercise), end, Modifier.fillMaxWidth())
        } else {
            SecondaryButton(stringResource(R.string.end_exercise), end, Modifier.fillMaxWidth())
        }
    }
    if (confirmNoPhotos) {
        ConfirmDialog(
            title = stringResource(R.string.no_photos_title),
            text = stringResource(R.string.no_photos_text),
            confirm = stringResource(R.string.finish_anyway),
            onConfirm = {
                confirmNoPhotos = false
                onEnd()
            },
            onDismiss = { confirmNoPhotos = false },
        )
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
                PhotoImage(path, Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
                if (path == null) {
                    Text("${i + 1}", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
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

@Preview(name = "Exercise EN", widthDp = 1100, heightDp = 800, showBackground = true)
@Composable
private fun ExercisePreviewEn() =
    MentalCoachTheme { ExerciseScreen(PreviewData.run, 1, 3, false, { PreviewCamera(it) }, {}, {}, {}) }

@Preview(name = "Exercise DE", widthDp = 400, heightDp = 900, showBackground = true, locale = "de")
@Composable
private fun ExercisePreviewDe() =
    MentalCoachTheme { ExerciseScreen(PreviewData.run, 1, 3, false, { PreviewCamera(it) }, {}, {}, {}) }
