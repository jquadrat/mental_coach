package com.mentalcoach.media

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.mentalcoach.domain.Lang

/** Speech-to-text. Prefers on-device recognition for privacy. Typing always remains possible. */
class Listener(
    private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() =
            SpeechRecognizer.isRecognitionAvailable(context) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    /**
     * Starts listening. [onText] receives the full transcript so far (partial results included);
     * [onEnd] is called once when recognition stops, with an error flag.
     */
    fun start(
        lang: Lang,
        onText: (String) -> Unit,
        onEnd: (error: Boolean) -> Unit,
    ) {
        stop()
        val rec =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        recognizer = rec
        var finished = false
        fun finish(error: Boolean) {
            if (finished) return
            finished = true
            onEnd(error)
        }
        rec.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    best(results)?.let(onText)
                    finish(false)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    best(partialResults)?.let(onText)
                }

                override fun onError(error: Int) = finish(error != SpeechRecognizer.ERROR_NO_MATCH)

                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            },
        )
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.locale().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            }
        try {
            rec.startListening(intent)
        } catch (e: SecurityException) {
            finish(true)
        }
    }

    fun stop() {
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
    }

    private fun best(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
