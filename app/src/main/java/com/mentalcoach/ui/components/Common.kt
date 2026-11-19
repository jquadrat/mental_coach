package com.mentalcoach.ui.components

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.domain.Lang
import com.mentalcoach.media.locale
import com.mentalcoach.ui.Busy
import com.mentalcoach.ui.ErrorKind
import com.mentalcoach.ui.UiError

/** True when the system asks for reduced motion (animator duration scale 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Shows the UI in the visit language, independent of the system locale. */
@Composable
fun Localized(
    lang: Lang,
    content: @Composable () -> Unit,
) {
    val base = LocalContext.current
    val config = LocalConfiguration.current
    val localized =
        remember(lang, config) {
            val c = Configuration(config).apply { setLocale(lang.locale()) }
            base.createConfigurationContext(c) to c
        }
    CompositionLocalProvider(
        LocalConfiguration provides localized.second,
        LocalResources provides localized.first.resources,
        LocalReducedMotion provides remember { reducedMotion(base) },
        content = content,
    )
}

/** Scrollable, centred column with a readable max width for tablets. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 880.dp)
                .fillMaxWidth()
                .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 56.dp)) { Text(text) }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun BusyOverlay(busy: Busy?) {
    if (busy == null) return
    val text =
        when (busy) {
            Busy.CHECKIN -> R.string.busy_checkin
            Busy.REFLECT -> R.string.busy_reflect
            Busy.LOGBOOK -> R.string.busy_logbook
        }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 4.dp) {
            Row(
                Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CircularProgressIndicator(Modifier.size(36.dp))
                Text(stringResource(text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

fun ErrorKind.message(): Int =
    when (this) {
        ErrorKind.NEED_NAME -> R.string.need_name
        ErrorKind.NEED_MOOD -> R.string.need_mood
        ErrorKind.NEED_CONSENT -> R.string.need_consent
        ErrorKind.NETWORK -> R.string.error_network
        ErrorKind.SERVER -> R.string.error_server
        ErrorKind.INVALID_REPLY -> R.string.error_invalid
        ErrorKind.NOT_CONFIGURED -> R.string.error_not_configured
        ErrorKind.PHOTO -> R.string.error_photo
        ErrorKind.EXPORT -> R.string.error_export
        ErrorKind.IMPORT -> R.string.error_import
    }

@Composable
fun ErrorDialog(
    error: UiError?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (error == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(error.kind.message()), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            if (error.retry != null) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
            }
        },
        dismissButton = if (error.retry != null) ({ TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) else null,
    )
}

@Composable
fun ConfirmDialog(
    title: String?,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
