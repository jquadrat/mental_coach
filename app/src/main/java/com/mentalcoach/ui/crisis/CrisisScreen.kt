package com.mentalcoach.ui.crisis

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.mentalcoach.R
import com.mentalcoach.ui.components.ConfirmDialog
import com.mentalcoach.ui.components.ScreenColumn
import com.mentalcoach.ui.components.SectionCard
import com.mentalcoach.ui.theme.MentalCoachTheme

/** Shown when the coach or the keyword check flags acute risk. The flow does not continue from here. */
@Composable
fun CrisisScreen(
    text: String?,
    helplines: String,
    onBackToStart: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val lines = helplines.ifBlank { stringResource(R.string.default_helplines) }.lines().map { it.trim() }.filter { it.isNotEmpty() }
    ScreenColumn {
        Text(stringResource(R.string.crisis_title), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(text ?: stringResource(R.string.crisis_default), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.crisis_staff), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SectionCard {
            Text(stringResource(R.string.crisis_contacts), style = MaterialTheme.typography.titleMedium)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
        TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth()) {
            Text(stringResource(R.string.crisis_back))
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = null,
            text = stringResource(R.string.crisis_back_confirm),
            confirm = stringResource(R.string.crisis_back),
            onConfirm = {
                confirm = false
                onBackToStart()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Preview(name = "Crisis EN", widthDp = 800, heightDp = 700, showBackground = true)
@Composable
private fun CrisisPreviewEn() = MentalCoachTheme { CrisisScreen(null, "", {}) }

@Preview(name = "Crisis DE", widthDp = 400, heightDp = 800, showBackground = true, locale = "de")
@Composable
private fun CrisisPreviewDe() = MentalCoachTheme { CrisisScreen(null, "", {}) }
