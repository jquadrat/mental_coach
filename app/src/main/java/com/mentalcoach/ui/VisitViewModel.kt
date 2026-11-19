package com.mentalcoach.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mentalcoach.data.api.CoachApi
import com.mentalcoach.data.api.CoachException
import com.mentalcoach.data.api.SessionPayload
import com.mentalcoach.data.repo.VisitRepository
import com.mentalcoach.data.settings.Settings
import com.mentalcoach.data.settings.SettingsRepository
import com.mentalcoach.domain.ChatTurn
import com.mentalcoach.domain.CheckinReading
import com.mentalcoach.domain.Exercise
import com.mentalcoach.domain.ExerciseLibrary
import com.mentalcoach.domain.ExerciseRun
import com.mentalcoach.domain.Lang
import com.mentalcoach.domain.PhotoSelection
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Screen
import com.mentalcoach.domain.Visit
import com.mentalcoach.media.PhotoStore
import com.mentalcoach.media.SpeechOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** UI copy the ViewModel needs to speak or store; comes from string resources in the visit language. */
interface VisitStrings {
    fun coachOpener(
        lang: Lang,
        name: String,
    ): String

    fun photoPrompt(
        lang: Lang,
        number: Int,
        prompt: String,
    ): String

    fun allPhotos(lang: Lang): String

    fun crisisDefault(lang: Lang): String

    fun crisisStaff(lang: Lang): String
}

/** Injectable clock and id source so tests are deterministic. */
interface Env {
    fun now(): Instant = Instant.now()

    fun newId(): String = UUID.randomUUID().toString()
}

data class CheckinDraft(
    val visitId: String = "",
    val name: String = "",
    val mood: String = "",
    val consent: Boolean = false,
    val profilePhoto: String? = null,
    val previousContext: String? = null,
)

enum class Busy { CHECKIN, REFLECT, LOGBOOK }

enum class ErrorKind { NEED_NAME, NEED_MOOD, NEED_CONSENT, NETWORK, SERVER, INVALID_REPLY, NOT_CONFIGURED, PHOTO, EXPORT, IMPORT }

enum class RetryAction { CHECKIN, REFLECT, LOGBOOK }

data class UiError(
    val kind: ErrorKind,
    val retry: RetryAction? = null,
)

data class VisitUiState(
    val restoring: Boolean = true,
    val visit: Visit? = null,
    val draft: CheckinDraft = CheckinDraft(),
    val chatDraft: String = "",
    val busy: Busy? = null,
    val error: UiError? = null,
    val settings: Settings = Settings(),
) {
    val screen: Screen get() = visit?.screen ?: Screen.CHECKIN
    val lang: Lang get() = visit?.lang ?: settings.lang
}

@HiltViewModel
class VisitViewModel
    @Inject
    constructor(
        private val coach: CoachApi,
        private val repo: VisitRepository,
        private val settingsRepo: SettingsRepository,
        private val library: ExerciseLibrary,
        private val photos: PhotoStore,
        private val speech: SpeechOutput,
        private val strings: VisitStrings,
        private val env: Env,
    ) : ViewModel() {
        private val _state = MutableStateFlow(VisitUiState())
        val state: StateFlow<VisitUiState> = _state.asStateFlow()

        private val visit get() = _state.value.visit

        /** The visit, only if it is on one of [screens]. Never continue the flow after a crisis. */
        private fun on(vararg screens: Screen): Visit? = visit?.takeIf { it.screen in screens && it.screen != Screen.CRISIS }

        init {
            viewModelScope.launch {
                val saved = runCatching { repo.current() }.getOrNull()
                val s = settingsRepo.settings.first()
                _state.update {
                    it.copy(
                        restoring = false,
                        visit = saved?.takeIf { v -> v.screen != Screen.CHECKIN },
                        draft = CheckinDraft(visitId = env.newId()),
                        settings = s,
                    )
                }
                if (saved != null && saved.screen == Screen.CHECKIN) repo.delete(saved)
                runCatching { photos.deleteAllExcept(_state.value.visit?.id) }
            }
            viewModelScope.launch {
                settingsRepo.settings.collect { s -> _state.update { it.copy(settings = s) } }
            }
        }

        // ------------------------------------------------------------ helpers

        private fun now() = env.now().toString()

        private suspend fun commit(v: Visit) {
            _state.update { it.copy(visit = v) }
            repo.save(v)
        }

        private fun speak(text: String) {
            val lang = _state.value.lang
            speech.stop()
            speech.speak(text, lang)
        }

        fun stopSpeaking() = speech.stop()

        fun dismissError() = _state.update { it.copy(error = null) }

        private fun fail(
            e: Throwable,
            retry: RetryAction,
        ) {
            val kind =
                when ((e as? CoachException)?.kind) {
                    CoachException.Kind.HTTP -> ErrorKind.SERVER
                    CoachException.Kind.INVALID_REPLY -> ErrorKind.INVALID_REPLY
                    CoachException.Kind.NOT_CONFIGURED -> ErrorKind.NOT_CONFIGURED
                    else -> ErrorKind.NETWORK
                }
            _state.update { it.copy(busy = null, error = UiError(kind, retry)) }
        }

        fun retry() {
            when (_state.value.error?.retry) {
                RetryAction.CHECKIN -> submitCheckin()
                RetryAction.REFLECT -> sendChat()
                RetryAction.LOGBOOK -> endVisit()
                null -> Unit
            }
        }

        private fun newRun(
            ex: Exercise,
            intro: String,
            lang: Lang,
        ) = ExerciseRun(
            exerciseId = ex.id,
            title = ex.title[lang],
            intro = intro,
            minutes = ex.minutes,
            steps = ex.steps[lang],
            photoPrompts = ex.photoPrompts[lang],
        )

        private suspend fun goCrisis(
            v: Visit,
            text: String?,
        ) {
            val msg = text?.takeIf { it.isNotBlank() } ?: strings.crisisDefault(v.lang)
            commit(v.copy(screen = Screen.CRISIS, crisisText = msg, suggestion = null))
            _state.update { it.copy(busy = null, chatDraft = "") }
            speak("$msg ${strings.crisisStaff(v.lang)}")
        }

        // ------------------------------------------------------------ 1. check-in

        fun updateName(v: String) = _state.update { it.copy(draft = it.draft.copy(name = v), error = null) }

        fun updateMood(v: String) = _state.update { it.copy(draft = it.draft.copy(mood = v), error = null) }

        fun setConsent(v: Boolean) = _state.update { it.copy(draft = it.draft.copy(consent = v), error = null) }

        fun setPreviousContext(text: String?) = _state.update { it.copy(draft = it.draft.copy(previousContext = text?.take(20_000))) }

        fun importFailed() = _state.update { it.copy(error = UiError(ErrorKind.IMPORT)) }

        fun onProfileCaptured(
            file: File,
            mirror: Boolean,
        ) {
            viewModelScope.launch {
                val d = _state.value.draft
                runCatching { photos.importCapture(d.visitId, file, mirror) }
                    .onSuccess { path ->
                        d.profilePhoto?.let { photos.delete(it) }
                        _state.update { it.copy(draft = it.draft.copy(profilePhoto = path)) }
                    }.onFailure { _state.update { it.copy(error = UiError(ErrorKind.PHOTO)) } }
            }
        }

        fun retakeProfile() {
            val p = _state.value.draft.profilePhoto ?: return
            viewModelScope.launch { photos.delete(p) }
            _state.update { it.copy(draft = it.draft.copy(profilePhoto = null)) }
        }

        fun submitCheckin() {
            val st = _state.value
            if (st.busy != null) return
            val d = st.draft
            val err =
                when {
                    d.name.isBlank() -> ErrorKind.NEED_NAME
                    d.mood.isBlank() -> ErrorKind.NEED_MOOD
                    !d.consent -> ErrorKind.NEED_CONSENT
                    else -> null
                }
            if (err != null) {
                _state.update { it.copy(error = UiError(err)) }
                return
            }
            speech.stop()
            val v =
                Visit(
                    id = d.visitId,
                    name = d.name.trim(),
                    lang = st.settings.lang,
                    maxExercises = st.settings.maxExercises,
                    mood = d.mood.trim(),
                    previousContext = d.previousContext,
                    profilePhoto = d.profilePhoto,
                    startedAt = now(),
                )
            _state.update { it.copy(busy = Busy.CHECKIN, error = null) }
            viewModelScope.launch {
                val r =
                    try {
                        coach.checkin(SessionPayload.from(v))
                    } catch (e: Exception) {
                        fail(e, RetryAction.CHECKIN)
                        return@launch
                    }
                val reading = CheckinReading(r.summary, r.feelings, r.energy, r.mood)
                val withReading = v.copy(checkin = reading, greeting = r.greeting)
                if (r.crisis) {
                    goCrisis(withReading, r.greeting)
                    return@launch
                }
                val ex = library.resolve(r.exerciseId)
                val run = newRun(ex, r.exerciseIntro, v.lang)
                commit(withReading.copy(exercises = listOf(run), screen = Screen.INTRO))
                _state.update { it.copy(busy = null) }
                speakIntro(r.greeting)
            }
        }

        // ------------------------------------------------------------ 2. instructions

        private fun speakIntro(greeting: String? = null) {
            val ex = visit?.current ?: return
            speak(listOfNotNull(greeting, ex.intro).plus(ex.steps).filter { it.isNotBlank() }.joinToString(" "))
        }

        fun repeatIntro() = speakIntro()

        // ------------------------------------------------------------ 3./4. exercise

        fun startExercise() {
            val v = on(Screen.INTRO) ?: return
            viewModelScope.launch {
                val nv = v.updateCurrent { it.copy(startedAt = it.startedAt ?: now()) }.copy(screen = Screen.EXERCISE)
                commit(nv)
                speakPhotoPrompt()
            }
        }

        private fun speakPhotoPrompt() {
            val v = visit ?: return
            val ex = v.current ?: return
            val n = ex.photos.size
            when {
                n < ex.photoPrompts.size -> speak(strings.photoPrompt(v.lang, n + 1, ex.photoPrompts[n]))
                n == ex.photoPrompts.size -> speak(strings.allPhotos(v.lang))
            }
        }

        fun onExercisePhoto(
            file: File,
            mirror: Boolean,
        ) {
            val v = on(Screen.EXERCISE)
            if (v == null || (v.current?.photos?.size ?: 0) >= PhotoSelection.MAX_PHOTOS) {
                file.delete()
                return
            }
            viewModelScope.launch {
                runCatching { photos.importCapture(v.id, file, mirror) }
                    .onSuccess { path ->
                        val cur = on(Screen.EXERCISE) ?: return@onSuccess
                        commit(cur.updateCurrent { it.copy(photos = it.photos + path) })
                        speakPhotoPrompt()
                    }.onFailure { _state.update { it.copy(error = UiError(ErrorKind.PHOTO)) } }
            }
        }

        fun deletePhoto(index: Int) {
            val v = on(Screen.EXERCISE) ?: return
            val path = v.current?.photos?.getOrNull(index) ?: return
            viewModelScope.launch {
                commit(
                    v.updateCurrent {
                        it.copy(photos = it.photos.filterIndexed { i, _ -> i != index }, selected = PhotoSelection.afterRemoval(it.selected, index))
                    },
                )
                photos.delete(path)
            }
        }

        fun endExercise() {
            val v = on(Screen.EXERCISE) ?: return
            viewModelScope.launch {
                val opener = strings.coachOpener(v.lang, v.name)
                commit(
                    v
                        .updateCurrent {
                            it.copy(
                                endedAt = now(),
                                selected = PhotoSelection.default(it.photos.size),
                                chat = it.chat + ChatTurn(Role.COACH, opener),
                            )
                        }.copy(screen = Screen.CHAT),
                )
                _state.update { it.copy(chatDraft = "") }
                speak(opener)
            }
        }

        // ------------------------------------------------------------ 5./6. reflection chat

        fun updateChatDraft(v: String) = _state.update { it.copy(chatDraft = v, error = null) }

        fun sendChat() {
            val st = _state.value
            val v = on(Screen.CHAT) ?: return
            val text = st.chatDraft.trim()
            if (text.isEmpty() || st.busy != null) return
            speech.stop()
            val withTurn = v.updateCurrent { it.copy(chat = it.chat + ChatTurn(Role.USER, text)) }
            _state.update { it.copy(visit = withTurn, chatDraft = "", busy = Busy.REFLECT, error = null) }
            viewModelScope.launch {
                val r =
                    try {
                        coach.reflect(SessionPayload.from(withTurn))
                    } catch (e: Exception) {
                        // Keep the visitor's words in the input so they can retry.
                        _state.update { it.copy(visit = v, chatDraft = text) }
                        fail(e, RetryAction.REFLECT)
                        return@launch
                    }
                val replied =
                    withTurn.updateCurrent {
                        val turns = if (r.reply.isBlank()) it.chat else it.chat + ChatTurn(Role.COACH, r.reply)
                        it.copy(chat = turns, moodNow = r.moodNow ?: it.moodNow)
                    }
                if (r.crisis) {
                    goCrisis(replied, r.reply)
                    return@launch
                }
                commit(replied.copy(suggestion = r.suggestion ?: replied.suggestion))
                _state.update { it.copy(busy = null) }
                val why = r.suggestion?.why
                speak(if (why == null || why == r.reply) r.reply else "${r.reply} $why")
            }
        }

        fun startNextExercise() {
            val v = on(Screen.CHAT) ?: return
            val sug = v.suggestion?.takeIf { it.isNext } ?: return
            val ex = library.resolve(sug.exerciseId, v.exercises.map { it.exerciseId })
            viewModelScope.launch {
                commit(v.copy(exercises = v.exercises + newRun(ex, sug.why, v.lang), suggestion = null, screen = Screen.INTRO))
                speakIntro()
            }
        }

        // ------------------------------------------------------------ 7. logbook

        fun endVisit() {
            val st = _state.value
            val v0 = on(Screen.INTRO, Screen.EXERCISE, Screen.CHAT) ?: return
            if (st.busy != null) return
            speech.stop()
            // Drop an exercise that was proposed but never started.
            val v = if (v0.current?.startedAt == null) v0.copy(exercises = v0.exercises.dropLast(1)) else v0
            _state.update { it.copy(busy = Busy.LOGBOOK, error = null) }
            viewModelScope.launch {
                val lb =
                    try {
                        coach.logbook(SessionPayload.from(v))
                    } catch (e: Exception) {
                        fail(e, RetryAction.LOGBOOK)
                        return@launch
                    }
                commit(v.copy(logbook = lb, endedAt = now(), suggestion = null, screen = Screen.LOGBOOK))
                _state.update { it.copy(busy = null) }
                speak("${lb.title}. ${lb.closing}")
            }
        }

        fun togglePhoto(
            exerciseIndex: Int,
            photoIndex: Int,
        ) = updateRun(exerciseIndex) { it.copy(selected = PhotoSelection.toggle(it.selected, photoIndex)) }

        fun dontCare(exerciseIndex: Int) = updateRun(exerciseIndex) { it.copy(selected = PhotoSelection.default(it.photos.size)) }

        private fun updateRun(
            i: Int,
            f: (ExerciseRun) -> ExerciseRun,
        ) {
            val v = on(Screen.LOGBOOK) ?: return
            if (i !in v.exercises.indices) return
            viewModelScope.launch { commit(v.copy(exercises = v.exercises.mapIndexed { k, r -> if (k == i) f(r) else r })) }
        }

        fun exportFailed() = _state.update { it.copy(error = UiError(ErrorKind.EXPORT)) }

        /** "Finish and start a new visit" (after the UI confirmed): deletes all data of this visit. */
        fun finishVisit() {
            val v = visit
            speech.stop()
            viewModelScope.launch {
                if (v != null) repo.delete(v)
                val oldDraft = _state.value.draft
                if (v == null || v.id != oldDraft.visitId) photos.deleteVisit(oldDraft.visitId)
                _state.update {
                    it.copy(visit = null, draft = CheckinDraft(visitId = env.newId()), chatDraft = "", busy = null, error = null)
                }
            }
        }

        // ------------------------------------------------------------ settings

        fun exerciseTitle(
            id: String,
            lang: Lang,
        ): String? = library.find(id)?.title?.get(lang)

        fun testVoice(sample: String) = speak(sample)

        fun updateSettings(f: (Settings) -> Settings) {
            viewModelScope.launch {
                settingsRepo.update(f)
                val s = settingsRepo.settings.first()
                val v = visit
                if (v != null && v.logbook == null && v.screen != Screen.CRISIS && s.maxExercises != v.maxExercises) {
                    commit(v.copy(maxExercises = s.maxExercises.coerceAtLeast(v.exercises.size)))
                }
            }
        }
    }
