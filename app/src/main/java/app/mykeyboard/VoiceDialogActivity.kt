package app.mykeyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/**
 * Voice typing: opens the phone's own speech dialog (Google's by default), then hands the recognised text back to the
 * keyboard. The microphone is used by that dialog, not by this app, so no microphone permission is needed here.
 */
class VoiceDialogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val lang = if (intent.getStringExtra("lang") == "ar") "ar-SA" else "en-US"
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_no_voice, Toast.LENGTH_LONG).show(); finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val text = if (resultCode == RESULT_OK) data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
        if (!text.isNullOrBlank()) {
            Prefs(this).sp.edit().putString(Prefs.K_VOICE_TEXT, text).putLong(Prefs.K_VOICE_TS, System.nanoTime()).apply()   // the keyboard picks this up
        }
        finish()
    }

    companion object { private const val REQ = 51 }
}
