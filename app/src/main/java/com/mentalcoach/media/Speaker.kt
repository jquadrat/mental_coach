package com.mentalcoach.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.mentalcoach.data.settings.Settings
import com.mentalcoach.domain.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

interface SpeechOutput {
    fun speak(
        text: String,
        lang: Lang,
    )

    fun stop()
}

fun Lang.locale(): Locale = if (this == Lang.DE) Locale.GERMANY else Locale.US

/** Text-to-speech, sentence by sentence, with a user-selectable voice and rate. */
class Speaker(
    context: Context,
) : SpeechOutput {
    private var ready = false
    private var pending: Pair<String, Lang>? = null
    private var settings = Settings()
    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices

    private val tts: TextToSpeech =
        TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                _voices.value = runCatching { tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired }.sortedBy { it.name } }
                    .getOrDefault(emptyList())
                pending?.let { (t, l) -> speak(t, l) }
                pending = null
            }
        }

    fun applySettings(s: Settings) {
        settings = s
        if (!s.voiceOn) stop()
    }

    override fun speak(
        text: String,
        lang: Lang,
    ) {
        if (!settings.voiceOn || text.isBlank()) return
        if (!ready) {
            pending = text to lang
            return
        }
        tts.stop()
        val locale = lang.locale()
        val voice = _voices.value.firstOrNull { it.name == settings.voiceName && it.locale.language == locale.language }
        if (voice != null) tts.voice = voice else tts.language = locale
        tts.setSpeechRate(settings.rate)
        splitSentences(text).forEachIndexed { i, sentence ->
            tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, "s$i")
        }
    }

    override fun stop() {
        pending = null
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()

    companion object {
        fun splitSentences(text: String): List<String> =
            Regex("[^.!?…]+[.!?…]*")
                .findAll(text)
                .map { it.value.trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .ifEmpty { listOf(text) }
    }
}
