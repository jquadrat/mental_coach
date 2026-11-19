package com.mentalcoach.ui.logbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.domain.Visit
import com.mentalcoach.media.logbookDate
import com.mentalcoach.ui.components.Avatar
import com.mentalcoach.ui.components.ConfirmDialog
import com.mentalcoach.ui.components.PhotoImage
import com.mentalcoach.ui.components.PreviewData
import com.mentalcoach.ui.components.PrimaryButton
import com.mentalcoach.ui.components.ScreenColumn
import com.mentalcoach.ui.components.SecondaryButton
import com.mentalcoach.ui.components.SectionCard
import com.mentalcoach.ui.theme.MentalCoachTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogbookScreen(
    visit: Visit,
    onTogglePhoto: (exercise: Int, photo: Int) -> Unit,
    onDontCare: (exercise: Int) -> Unit,
    onSavePdf: () -> Unit,
    onSaveHtml: () -> Unit,
    onSaveContext: () -> Unit,
    onNewVisit: () -> Unit,
) {
    val lb = visit.logbook ?: return
    var confirm by remember { mutableStateOf(false) }
    ScreenColumn {
        SectionCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visit.profilePhoto != null) Avatar(visit.profilePhoto, 132)
                Text(lb.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.logbook_meta, visit.name, logbookDate(visit)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(lb.opening, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
        if (visit.exercises.any { it.photos.size > 1 }) {
            Text(stringResource(R.string.logbook_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visit.exercises.forEachIndexed { i, ex ->
            val entry = lb.entries.firstOrNull { it.exerciseIndex == i }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${ex.title}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (ex.photos.size > 1) TextButton(onClick = { onDontCare(i) }) { Text(stringResource(R.string.dont_care)) }
                }
                if (ex.photos.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ex.photos.forEachIndexed { p, path ->
                            val on = p in ex.selected
                            Box(
                                Modifier
                                    .width(150.dp)
                                    .aspectRatio(4f / 3f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .border(BorderStroke(if (on) 4.dp else 0.dp, MaterialTheme.colorScheme.secondary), MaterialTheme.shapes.medium)
                                    .semantics { selected = on }
                                    .clickable(role = Role.Checkbox) { onTogglePhoto(i, p) },
                            ) {
                                PhotoImage(path, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                                if (on) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
                entry?.narrative?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (ex.notes.isNotEmpty()) {
                    Text(stringResource(R.string.your_notes).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ex.notes.forEach { Text("„${it.text}“", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyLarge) }
                }
                if (!entry?.takeaway.isNullOrBlank()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.takeaway).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.takeaway, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Text(lb.closing, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(stringResource(R.string.save_pdf), onSavePdf, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(stringResource(R.string.save_html), onSaveHtml, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.save_context), onSaveContext, Modifier.weight(1f))
            }
            TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.new_visit))
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.new_visit_confirm_title),
            text = stringResource(R.string.new_visit_confirm),
            confirm = stringResource(R.string.delete_and_continue),
            onConfirm = {
                confirm = false
                onNewVisit()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Preview(name = "Logbook EN", widthDp = 900, heightDp = 1100, showBackground = true)
@Composable
private fun LogbookPreviewEn() = MentalCoachTheme { LogbookScreen(PreviewData.visit, { _, _ -> }, {}, {}, {}, {}, {}) }

@Preview(name = "Logbook DE", widthDp = 400, heightDp = 900, showBackground = true, locale = "de")
@Composable
private fun LogbookPreviewDe() = MentalCoachTheme { LogbookScreen(PreviewData.visit, { _, _ -> }, {}, {}, {}, {}, {}) }
