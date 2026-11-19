package com.mentalcoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.data.settings.Settings
import com.mentalcoach.domain.Lang
import com.mentalcoach.ui.components.ScreenColumn
import com.mentalcoach.ui.components.SecondaryButton
import com.mentalcoach.ui.components.SectionCard
import com.mentalcoach.ui.theme.MentalCoachTheme
import kotlin.math.roundToInt

/** A TTS voice option: [id] is stored in settings, [label] is shown. */
data class VoiceOption(
    val id: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    voices: List<VoiceOption>,
    showDemoToggle: Boolean,
    languageLocked: Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
    onTestVoice: () -> Unit,
) {
    ScreenColumn {
        SectionCard {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Lang.entries.forEach { l ->
                    FilterChip(
                        selected = settings.lang == l,
                        enabled = !languageLocked,
                        onClick = { onChange { it.copy(lang = l, voiceName = "") } },
                        label = { Text(stringResource(if (l == Lang.DE) R.string.lang_de else R.string.lang_en)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        SectionCard {
            var n by remember(settings.maxExercises) { mutableFloatStateOf(settings.maxExercises.toFloat()) }
            Text(stringResource(R.string.exercise_count, n.roundToInt()), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = n,
                onValueChange = { n = it },
                onValueChangeFinished = { onChange { s -> s.copy(maxExercises = n.roundToInt()) } },
                valueRange = 1f..10f,
                steps = 8,
            )
        }
        SectionCard {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(settings.voiceOn, role = Role.Switch) { v -> onChange { it.copy(voiceOn = v) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.speak_aloud), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = settings.voiceOn, onCheckedChange = null)
            }
            var open by remember { mutableStateOf(false) }
            val auto = stringResource(R.string.voice_auto)
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = voices.firstOrNull { it.id == settings.voiceName }?.label ?: auto,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.voice)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    (listOf(VoiceOption("", auto)) + voices).forEach { v ->
                        DropdownMenuItem(text = { Text(v.label) }, onClick = {
                            open = false
                            onChange { it.copy(voiceName = v.id) }
                        })
                    }
                }
            }
            var rate by remember(settings.rate) { mutableFloatStateOf(settings.rate) }
            Text(stringResource(R.string.rate, rate))
            Slider(value = rate, onValueChange = { rate = it }, onValueChangeFinished = { onChange { s -> s.copy(rate = rate) } }, valueRange = 0.6f..1.4f)
            SecondaryButton(stringResource(R.string.test_voice), onTestVoice)
        }
        SectionCard {
            var text by remember { mutableStateOf(settings.helplines) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChange { s -> s.copy(helplines = it.trim()) }
                },
                label = { Text(stringResource(R.string.helplines)) },
                placeholder = { Text(stringResource(R.string.default_helplines)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showDemoToggle) {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(settings.demoMode, role = Role.Switch) { v -> onChange { it.copy(demoMode = v) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.demo_mode), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = settings.demoMode, onCheckedChange = null)
                }
            }
        }
        Text(stringResource(R.string.settings_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Settings EN", widthDp = 800, heightDp = 1000, showBackground = true)
@Composable
private fun SettingsPreviewEn() = MentalCoachTheme { SettingsScreen(Settings(), emptyList(), true, false, {}, {}) }

@Preview(name = "Settings DE", widthDp = 400, heightDp = 1000, showBackground = true, locale = "de")
@Composable
private fun SettingsPreviewDe() = MentalCoachTheme { SettingsScreen(Settings(lang = Lang.DE), emptyList(), true, false, {}, {}) }
