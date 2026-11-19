package com.mentalcoach.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.mentalcoach.domain.Lang

/** Speech-to-text. Prefers on-device recognition for privacy. Typing always remains possible. */
class Listener(
    private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    /**
     * The recognition service to use: the system default if it is a real recognition service, otherwise Google's.
     * On Fire OS the default points to Alexa, which is not one, so the plain default recogniser would fail.
     */
    private val service: ComponentName? by lazy {
        val services =
            context.packageManager
                .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
        // The system default wins when it is a real recognition service (e.g. on a Pixel).
        val default =
            android.provider.Settings.Secure
                .getString(context.contentResolver, "voice_recognition_service")
                ?.let(ComponentName::unflattenFromString)
        services.firstOrNull { it == default }
            ?: services.firstOrNull { it.packageName == GOOGLE_APP }
            ?: services.firstOrNull()
    }

    val isAvailable: Boolean
        get() =
            service != null ||
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
                SpeechRecognizer.createSpeechRecognizer(context, service)
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

                override fun onError(error: Int) {
                    // Only the numeric error code is logged, never what the visitor said.
                    android.util.Log.w("Listener", "Speech recognition error $error")
                    finish(error != SpeechRecognizer.ERROR_NO_MATCH)
                }

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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
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

    companion object {
        private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    }

    private fun best(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
