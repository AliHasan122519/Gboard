package app.mykeyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Voice dictation using the speech service installed on the device (usually Google's). Audio is handled by that
 * service, never by this app. Partial results are shown live, the final text is committed by the keyboard.
 */
class VoiceInput(private val ctx: Context, private val cb: Callback) {
    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onState(listening: Boolean)
        fun onError(messageRes: Int)
    }

    private var rec: SpeechRecognizer? = null
    var listening = false
        private set

    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)
    fun hasPermission() = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(lang: String) {
        stopQuietly()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { set(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) { first(b)?.let { cb.onPartial(it) } }
            override fun onResults(b: Bundle?) { first(b)?.let { cb.onFinal(it) }; finish() }
            override fun onError(code: Int) {
                cb.onError(when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_no_speech
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> R.string.voice_network
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_permission
                    SpeechRecognizer.ERROR_CLIENT -> 0
                    else -> R.string.voice_error
                })
                finish()
            }
        })
        val tag = if (lang == "ar") "ar-SA" else "en-US"
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        })
        set(true)
    }

    /** Ask the recognizer to finish; the final result still arrives through the callback. */
    fun stop() { rec?.stopListening() }

    fun cancel() { stopQuietly(); set(false) }

    private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun finish() { rec?.destroy(); rec = null; set(false) }

    private fun stopQuietly() { runCatching { rec?.cancel(); rec?.destroy() }; rec = null }

    private fun set(v: Boolean) { if (listening != v) { listening = v; cb.onState(v) } }
}
