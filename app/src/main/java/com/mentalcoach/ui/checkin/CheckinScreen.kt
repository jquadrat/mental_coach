package com.mentalcoach.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.domain.Lang
import com.mentalcoach.ui.CheckinDraft
import com.mentalcoach.ui.components.MicButton
import com.mentalcoach.ui.components.PhotoImage
import com.mentalcoach.ui.components.PreviewCamera
import com.mentalcoach.ui.components.PrimaryButton
import com.mentalcoach.ui.components.ScreenColumn
import com.mentalcoach.ui.components.SecondaryButton
import com.mentalcoach.ui.components.SectionCard
import com.mentalcoach.ui.theme.MentalCoachTheme

@Composable
fun CheckinScreen(
    draft: CheckinDraft,
    lang: Lang,
    cameraReady: Boolean,
    camera: @Composable (Modifier) -> Unit,
    onShutter: () -> Unit,
    onRetake: () -> Unit,
    onName: (String) -> Unit,
    onMood: (String) -> Unit,
    onConsent: (Boolean) -> Unit,
    onLoadNotes: () -> Unit,
    onRemoveNotes: () -> Unit,
    onSubmit: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    ScreenColumn {
        Text(stringResource(R.string.checkin_title), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.checkin_lead), style = MaterialTheme.typography.bodyLarge)
        BoxWithConstraints {
            val wide = maxWidth >= 640.dp
            val photo = @Composable { m: Modifier -> ProfilePhoto(draft.profilePhoto, cameraReady, camera, onShutter, onRetake, m) }
            val form =
                @Composable { m: Modifier ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = onName,
                            label = { Text(stringResource(R.string.name_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.mood_label), style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.mood,
                                onValueChange = onMood,
                                placeholder = { Text(stringResource(R.string.mood_placeholder)) },
                                minLines = 4,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.weight(1f),
                            )
                            MicButton(lang, draft.mood, onMood, onStopSpeaking)
                        }
                    }
                }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    photo(Modifier.weight(0.42f))
                    form(Modifier.weight(0.58f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    photo(Modifier.fillMaxWidth())
                    form(Modifier.fillMaxWidth())
                }
            }
        }
        SectionCard {
            Text(stringResource(R.string.previous_title), style = MaterialTheme.typography.titleMedium)
            if (draft.previousContext != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.context_loaded), modifier = Modifier.weight(1f))
                    TextButton(onClick = onRemoveNotes, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.remove_notes)) }
                }
            } else {
                Text(stringResource(R.string.previous_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(stringResource(R.string.load_notes), onLoadNotes)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .toggleable(value = draft.consent, onValueChange = onConsent, role = Role.Checkbox)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = draft.consent, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Text(stringResource(R.string.consent), style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(stringResource(R.string.checkin_button), onSubmit, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProfilePhoto(
    path: String?,
    cameraReady: Boolean,
    camera: @Composable (Modifier) -> Unit,
    onShutter: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.photo_label), style = MaterialTheme.typography.titleMedium)
        val box = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
        if (path != null) {
            PhotoImage(path, box.clip(MaterialTheme.shapes.large))
            SecondaryButton(stringResource(R.string.retake), onRetake, Modifier.fillMaxWidth())
        } else {
            camera(box)
            PrimaryButton(stringResource(R.string.take_photo), onShutter, Modifier.fillMaxWidth(), enabled = cameraReady)
        }
        Text(stringResource(R.string.photo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckinPreview() {
    MentalCoachTheme {
        CheckinScreen(
            CheckinDraft(name = "Ana", mood = "A bit stressed after a long week.", consent = true),
            Lang.EN, false, { PreviewCamera(it) }, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Check-in EN", widthDp = 1000, heightDp = 800, showBackground = true)
@Composable
private fun CheckinPreviewEn() = CheckinPreview()

@Preview(name = "Check-in DE", widthDp = 400, heightDp = 900, showBackground = true, locale = "de")
@Composable
private fun CheckinPreviewDe() = CheckinPreview()
