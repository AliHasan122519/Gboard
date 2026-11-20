package app.mykeyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Shows the last crash report (runs in its own process so it survives the crash). */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = runCatching { CrashReporter.file(this).readText() }.getOrDefault("(no report)")
        val p = Palette.DARK
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.background); setPadding(Ui.dpi(this@CrashActivity, 16f), Ui.dpi(this@CrashActivity, 40f), Ui.dpi(this@CrashActivity, 16f), Ui.dpi(this@CrashActivity, 16f)) }
        root.addView(Ui.text(this, getString(R.string.crash_title), 20f, p.label, Gravity.START).apply { setTypeface(typeface, Typeface.BOLD) })
        root.addView(Ui.text(this, getString(R.string.crash_body), 14f, p.hint, Gravity.START).apply { setPadding(0, Ui.dpi(this@CrashActivity, 6f), 0, Ui.dpi(this@CrashActivity, 10f)) })
        val sv = ScrollView(this)
        sv.addView(TextView(this).apply { text = report; setTextIsSelectable(true); textSize = 11f; setTextColor(p.label); typeface = Typeface.MONOSPACE })
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, Ui.dpi(this@CrashActivity, 10f), 0, 0) }
        val copy = Ui.pill(this, getString(R.string.crash_copy), p.accent, p.onAccent)
        Ui.pressable(copy, {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", report))
            Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
        })
        val share = Ui.pill(this, getString(R.string.crash_share), p.key, p.label)
        Ui.pressable(share, {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), null))
        })
        row.addView(copy, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = Ui.dpi(this@CrashActivity, 8f) })
        row.addView(share)
        root.addView(row)
        setContentView(root)
    }
}
