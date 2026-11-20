package app.mykeyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible helper started from the keyboard's "Add font" button. Opens the system file picker, copies the chosen
 * .ttf/.otf files into the app and selects the last one. The keyboard (same process) picks the change up live.
 */
class FontImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf",
                    "application/vnd.ms-opentype", "application/font-sfnt", "application/octet-stream"),
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.font_import_failed, Toast.LENGTH_LONG).show(); finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ || resultCode != RESULT_OK || data == null) { finish(); return }
        val uris = ArrayList<Uri>()
        data.clipData?.let { cd -> for (k in 0 until cd.itemCount) uris.add(cd.getItemAt(k).uri) }
        data.data?.let { if (it !in uris) uris.add(it) }
        val appCtx = applicationContext
        Thread {
            val repo = FontRepository(appCtx)
            val prefs = Prefs(appCtx)
            var ok = 0; var last: String? = null
            for (u in uris) repo.importFont(u)?.let { ok++; last = it }
            runOnUiThread {
                if (last != null) {
                    prefs.fontId = last!!
                    prefs.sp.edit().putLong(KeyboardService.FONT_LIST_VERSION, System.nanoTime()).apply()
                    Toast.makeText(appCtx, appCtx.getString(R.string.font_import_ok, ok), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appCtx, R.string.font_import_failed, Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }.start()
    }

    companion object { private const val REQ = 41 }
}
