package app.mykeyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/** Invisible helper: asks for the microphone permission (a keyboard cannot show that dialog itself). */
class MicPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { grantedDone(); return }
        if (savedInstanceState == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 11)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) grantedDone()
        else { Toast.makeText(this, R.string.voice_permission, Toast.LENGTH_LONG).show(); finish() }
    }

    private fun grantedDone() {
        Prefs(this).sp.edit().putLong(Prefs.K_MIC_GRANT, System.nanoTime()).apply()   // the keyboard listens for this and starts dictation
        finish()
    }
}
