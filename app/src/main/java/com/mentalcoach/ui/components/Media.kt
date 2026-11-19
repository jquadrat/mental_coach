package com.mentalcoach.ui.components

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mentalcoach.R
import com.mentalcoach.domain.Lang
import com.mentalcoach.media.CameraController
import com.mentalcoach.media.Listener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runtime permission requested at the point of use, after a short explanation. */
class PermissionState(
    granted: Boolean,
) {
    var granted by mutableStateOf(granted)
    var denied by mutableStateOf(false)
    var request: () -> Unit = {}
}

@Composable
fun rememberPermission(permission: String): PermissionState {
    val context = LocalContext.current
    val state =
        remember {
            PermissionState(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
        }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            state.granted = ok
            state.denied = !ok
        }
    state.request = { launcher.launch(permission) }
    return state
}

/** Loads a local photo downscaled for display. Photos never leave app storage. */
@Composable
fun rememberPhoto(
    path: String?,
    maxSide: Int = 640,
): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value =
            path?.let {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(it, o)
                        var s = 1
                        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
                        BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
                    }.getOrNull()
                }
            }
    }
    return bmp
}

@Composable
fun PhotoImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bmp = rememberPhoto(path)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bmp != null) Image(bmp, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
    }
}

/** Live camera preview with a front/back toggle, or an explanation / fallback when no camera is allowed. */
@Composable
fun CameraBox(
    controller: CameraController?,
    permission: PermissionState?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            controller == null || permission == null || LocalInspectionMode.current -> CameraUnavailable(stringResource(R.string.camera_why), null)
            permission.granted && !controller.failed -> {
                val owner = LocalLifecycleOwner.current
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            controller.bind(owner, this)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                DisposableEffect(controller) { onDispose { controller.unbind() } }
                if (controller.canFlip) {
                    FilledTonalIconButton(
                        onClick = controller::flip,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(56.dp),
                    ) { Icon(Icons.Default.Cameraswitch, stringResource(R.string.switch_camera)) }
                }
            }
            permission.denied || controller.failed -> CameraUnavailable(stringResource(R.string.camera_denied), null)
            else -> CameraUnavailable(stringResource(R.string.camera_why)) { permission.request() }
        }
    }
}

@Composable
private fun CameraUnavailable(
    text: String,
    onAllow: (() -> Unit)?,
) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.NoPhotography, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onAllow != null) SecondaryButton(stringResource(R.string.allow_camera), onAllow)
    }
}

/**
 * Speech-to-text button. Appends what the visitor says to [current] via [onText].
 * Asks for the microphone with an explanation first. Hidden when no recogniser exists.
 */
@Composable
fun MicButton(
    lang: Lang,
    current: String,
    onText: (String) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        FilledTonalIconButton(onClick = {}, modifier = modifier.size(56.dp)) { Icon(Icons.Default.Mic, null) }
        return
    }
    val context = LocalContext.current
    val listener = remember { Listener(context) }
    if (!listener.isAvailable) return
    val perm = rememberPermission(android.Manifest.permission.RECORD_AUDIO)
    var listening by remember { mutableStateOf(false) }
    var explain by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { listener.stop() } }

    fun start() {
        onStart()
        val base = current.trimEnd().let { if (it.isEmpty()) "" else "$it " }
        listening = true
        listener.start(lang, onText = { onText(base + it) }, onEnd = { listening = false })
    }

    FilledTonalIconToggleButton(
        checked = listening,
        onCheckedChange = {
            when {
                listening -> {
                    listener.stop()
                    listening = false
                }
                perm.granted -> start()
                else -> explain = true
            }
        },
        modifier = modifier.size(56.dp),
        colors =
            IconButtonDefaults.filledTonalIconToggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.secondary,
                checkedContentColor = MaterialTheme.colorScheme.onSecondary,
            ),
    ) {
        Icon(
            if (perm.denied) Icons.Default.MicOff else Icons.Default.Mic,
            stringResource(if (listening) R.string.stop_listening else R.string.speak),
        )
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            icon = { Icon(Icons.Default.Mic, null) },
            text = { Text(stringResource(if (perm.denied) R.string.mic_denied else R.string.mic_why)) },
            confirmButton = {
                if (!perm.denied) {
                    TextButton(onClick = {
                        explain = false
                        perm.request()
                    }) { Text(stringResource(R.string.allow)) }
                }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.not_now)) } },
        )
    }
}

/** Round avatar from a local photo. */
@Composable
fun Avatar(
    path: String?,
    size: Int,
) {
    PhotoImage(path, Modifier.size(size.dp).clip(CircleShape))
}
