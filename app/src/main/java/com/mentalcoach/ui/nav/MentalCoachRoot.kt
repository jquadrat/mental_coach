package com.mentalcoach.ui.nav

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mentalcoach.BuildConfig
import com.mentalcoach.R
import com.mentalcoach.domain.Screen
import com.mentalcoach.media.CameraController
import com.mentalcoach.media.HtmlExporter
import com.mentalcoach.media.LogbookLabels
import com.mentalcoach.media.MarkdownExporter
import com.mentalcoach.media.PdfExporter
import com.mentalcoach.media.PhotoStore
import com.mentalcoach.media.Speaker
import com.mentalcoach.media.fileStem
import com.mentalcoach.media.logbookDate
import com.mentalcoach.ui.VisitViewModel
import com.mentalcoach.ui.chat.ChatScreen
import com.mentalcoach.ui.checkin.CheckinScreen
import com.mentalcoach.ui.components.BusyOverlay
import com.mentalcoach.ui.components.CameraBox
import com.mentalcoach.ui.components.ErrorDialog
import com.mentalcoach.ui.components.Localized
import com.mentalcoach.ui.components.rememberPermission
import com.mentalcoach.ui.crisis.CrisisScreen
import com.mentalcoach.ui.exercise.ExerciseActions
import com.mentalcoach.ui.exercise.ExerciseScreen
import com.mentalcoach.ui.intro.IntroScreen
import com.mentalcoach.ui.logbook.LogbookScreen
import com.mentalcoach.ui.settings.SettingsScreen
import com.mentalcoach.ui.settings.VoiceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SETTINGS = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalCoachRoot(
    vm: VisitViewModel,
    speaker: Speaker,
    photoStore: PhotoStore,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.restoring) return
    LaunchedEffect(state.settings) { speaker.applySettings(state.settings) }

    Localized(state.lang) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // The ViewModel decides the flow step; navigation follows it.
        LaunchedEffect(state.screen) {
            if (nav.currentDestination?.route != state.screen.route) {
                nav.navigate(state.screen.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        // Stop speaking whenever the visitor leaves a screen.
        LaunchedEffect(route) { if (route == SETTINGS) vm.stopSpeaking() }

        val camera = remember { CameraController(context) }
        val cameraPerm = rememberPermission(Manifest.permission.CAMERA)
        fun shoot(onFile: (java.io.File, Boolean) -> Unit) {
            scope.launch {
                val f = withContext(Dispatchers.IO) { photoStore.tempFile() }
                if (camera.capture(f)) onFile(f, camera.front) else f.delete()
            }
        }

        val openNotes =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val text =
                        withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                        }
                    if (text.isNullOrBlank()) vm.importFailed() else vm.setPreviousContext(text)
                }
            }

        val takeaway = stringResource(R.string.takeaway)
        val visit = state.visit
        val meta = visit?.let { stringResource(R.string.logbook_meta, it.name, logbookDate(it)) }.orEmpty()
        val savedMsg = stringResource(R.string.saved)
        fun saver(write: (java.io.OutputStream) -> Unit) = { uri: android.net.Uri? ->
            if (uri != null) {
                scope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use(write) }.isSuccess
                        }
                    if (ok) Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show() else vm.exportFailed()
                }
            }
            Unit
        }
        val yourNotes = stringResource(R.string.your_notes)
        val labels = LogbookLabels(takeaway, meta, yourNotes)
        val voiceSample = stringResource(R.string.checkin_lead)
        val savePdf =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                saver { out -> vm.state.value.visit?.let { PdfExporter.export(it, labels, out) } }(uri)
            }
        val saveHtml =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
                saver { out -> vm.state.value.visit?.let { out.write(HtmlExporter.export(it, labels).toByteArray()) } }(uri)
            }
        val saveMd =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
                saver { out -> vm.state.value.visit?.let { out.write(MarkdownExporter().export(it).toByteArray()) } }(uri)
            }

        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        if (route == SETTINGS) {
                            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        }
                    },
                    title = { Text(stringResource(if (route == SETTINGS) R.string.settings else R.string.app_name)) },
                    actions = {
                        val v = state.visit
                        if (v != null && route != SETTINGS && v.exercises.isNotEmpty() && v.screen in listOf(Screen.INTRO, Screen.EXERCISE, Screen.CHAT)) {
                            Text(
                                stringResource(R.string.exercise_of, v.exercises.size, v.maxExercises),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.settings.demoMode) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.demo_badge)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            )
                        }
                        IconButton(onClick = { vm.updateSettings { it.copy(voiceOn = !it.voiceOn) } }) {
                            if (state.settings.voiceOn) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.voice_on))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.VolumeOff, stringResource(R.string.voice_off))
                            }
                        }
                        if (route != SETTINGS) {
                            IconButton(onClick = { nav.navigate(SETTINGS) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                NavHost(nav, startDestination = state.screen.route) {
                    composable(Screen.CHECKIN.route) {
                        CheckinScreen(
                            draft = state.draft,
                            lang = state.lang,
                            cameraReady = camera.ready && cameraPerm.granted,
                            camera = { CameraBox(camera, cameraPerm, it) },
                            onShutter = { shoot(vm::onProfileCaptured) },
                            onRetake = vm::retakeProfile,
                            onName = vm::updateName,
                            onMood = vm::updateMood,
                            onConsent = vm::setConsent,
                            onLoadNotes = { openNotes.launch(arrayOf("text/*", "application/octet-stream")) },
                            onRemoveNotes = { vm.setPreviousContext(null) },
                            onSubmit = vm::submitCheckin,
                            onStopSpeaking = vm::stopSpeaking,
                        )
                    }
                    composable(Screen.INTRO.route) {
                        BackHandler {}
                        val v = state.visit ?: return@composable
                        val run = v.current ?: return@composable
                        IntroScreen(v.checkin, run, v.exercises.size, v.maxExercises, vm::repeatIntro, vm::startExercise, vm::endVisit)
                    }
                    composable(Screen.EXERCISE.route) {
                        BackHandler {}
                        val v = state.visit ?: return@composable
                        val run = v.current ?: return@composable
                        ExerciseScreen(
                            run = run,
                            index = v.exercises.size,
                            max = v.maxExercises,
                            lang = v.lang,
                            noteDraft = state.noteDraft,
                            cameraReady = camera.ready && cameraPerm.granted,
                            camera = { CameraBox(camera, cameraPerm, it) },
                            actions =
                                ExerciseActions(
                                    onShutter = { shoot(vm::onExercisePhoto) },
                                    onDeletePhoto = vm::deletePhoto,
                                    onNoteDraft = vm::updateNoteDraft,
                                    onSaveNote = vm::addNote,
                                    onDeleteNote = vm::deleteNote,
                                    onStep = vm::goToStep,
                                    onEnd = vm::endExercise,
                                    onStopSpeaking = vm::stopSpeaking,
                                ),
                        )
                    }
                    composable(Screen.CHAT.route) {
                        BackHandler {}
                        val v = state.visit ?: return@composable
                        val run = v.current ?: return@composable
                        val next = v.suggestion?.exerciseId?.let { id -> vm.exerciseTitle(id, v.lang) }
                        ChatScreen(
                            chat = run.chat,
                            suggestion = v.suggestion,
                            nextTitle = next,
                            draft = state.chatDraft,
                            lang = v.lang,
                            sending = state.busy != null,
                            onDraft = vm::updateChatDraft,
                            onSend = vm::sendChat,
                            onNext = vm::startNextExercise,
                            onEndVisit = vm::endVisit,
                            onStopSpeaking = vm::stopSpeaking,
                        )
                    }
                    composable(Screen.LOGBOOK.route) {
                        BackHandler {}
                        val v = state.visit ?: return@composable
                        LogbookScreen(
                            visit = v,
                            onTogglePhoto = vm::togglePhoto,
                            onDontCare = vm::dontCare,
                            onSavePdf = { savePdf.launch("${fileStem(v)}-logbook.pdf") },
                            onSaveHtml = { saveHtml.launch("${fileStem(v)}-logbook.html") },
                            onSaveContext = { saveMd.launch("${fileStem(v)}-context.md") },
                            onNewVisit = vm::finishVisit,
                        )
                    }
                    composable(Screen.CRISIS.route) {
                        // The crisis screen cannot be skipped with the back button.
                        BackHandler {}
                        CrisisScreen(state.visit?.crisisText, state.settings.helplines, vm::finishVisit)
                    }
                    composable(SETTINGS) {
                        val voices by speaker.voices.collectAsState()
                        val lang = state.lang
                        SettingsScreen(
                            settings = state.settings,
                            voices =
                                voices
                                    .filter { it.locale.language == lang.code }
                                    .map { VoiceOption(it.name, "${it.name} (${it.locale.toLanguageTag()})") },
                            showDemoToggle = BuildConfig.DEBUG,
                            languageLocked = state.visit != null,
                            onChange = vm::updateSettings,
                            onTestVoice = { vm.testVoice(voiceSample) },
                        )
                    }
                }
                BusyOverlay(state.busy)
                ErrorDialog(state.error, vm::retry, vm::dismissError)
            }
        }
    }
}
