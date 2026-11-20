package app.mykeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.ProgressBar
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.mykeyboard.update.CheckResult
import app.mykeyboard.update.UpdateInfo
import app.mykeyboard.update.UpdateManager

/** Setup + settings screen. Fonts themselves are changed from the keyboard, not from here. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var p: Palette
    private lateinit var step1Status: TextView
    private lateinit var step2Status: TextView
    private lateinit var content: LinearLayout
    private lateinit var updater: UpdateManager
    private val main = Handler(Looper.getMainLooper())
    private lateinit var upStatus: TextView
    private lateinit var upNotes: TextView
    private lateinit var upProgress: ProgressBar
    private lateinit var upCheck: TextView
    private lateinit var upInstall: TextView
    private var pending: UpdateInfo? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        updater = UpdateManager(this)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        p = if (night) Palette.DARK else Palette.LIGHT

        val scroll = ScrollView(this).apply { setBackgroundColor(p.background); isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val (t, b) = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()); i.top to i.bottom
            } else @Suppress("DEPRECATION") (insets.systemWindowInsetTop to insets.systemWindowInsetBottom)
            v.setPadding(0, t, 0, b); insets
        }
        setContentView(scroll)
        build()
    }

    override fun onResume() {
        super.onResume(); refreshStatus()
        pending = UpdateInfo.fromJson(prefs.availableUpdate)
        if (pending != null && !busy) showAvailable(pending!!)
        if (prefs.lastInstallError.isNotEmpty()) upStatus.text = getString(R.string.update_install_error, prefs.lastInstallError)
        if (prefs.autoUpdate && !busy && System.currentTimeMillis() - prefs.lastUpdateCheck > 12L * 3600 * 1000) checkNow(false)
    }

    private fun dp(v: Float) = Ui.dpi(this, v)

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.round(p.key, Ui.dp(this@MainActivity, 20f))
        setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
        content.addView(this, Ui.lp(Ui.MATCH, Ui.WRAP).apply { setMargins(dp(16f), dp(8f), dp(16f), dp(4f)) })
    }

    private fun title(parent: LinearLayout, s: String) =
        parent.addView(Ui.text(this, s, 17f, p.label, Gravity.START).apply { setTypeface(typeface, Typeface.BOLD) })

    private fun body(parent: LinearLayout, s: String, color: Int = p.hint) =
        parent.addView(Ui.text(this, s, 14f, color, Gravity.START).apply { setPadding(0, dp(4f), 0, dp(4f)); setLineSpacing(0f, 1.15f) })

    private fun button(parent: LinearLayout, s: String, onClick: () -> Unit) {
        val b = Ui.pill(this, s, p.accent, p.onAccent, 14f)
        Ui.pressable(b, onClick)
        parent.addView(b, Ui.lp(Ui.WRAP, Ui.WRAP).apply { topMargin = dp(8f) })
    }

    private fun build() {
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24f), dp(28f), dp(24f), dp(12f)) }
        head.addView(Ui.text(this, getString(R.string.app_name), 30f, p.label, Gravity.START).apply { setTypeface(typeface, Typeface.BOLD) })
        head.addView(Ui.text(this, getString(R.string.main_subtitle), 15f, p.hint, Gravity.START).apply { setPadding(0, dp(4f), 0, 0) })
        content.addView(head)

        card().also { c ->
            title(c, getString(R.string.step1_title)); body(c, getString(R.string.step1_desc))
            step1Status = Ui.text(this, "", 13f, p.accent, Gravity.START); c.addView(step1Status)
            button(c, getString(R.string.step1_button)) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        card().also { c ->
            title(c, getString(R.string.step2_title)); body(c, getString(R.string.step2_desc))
            step2Status = Ui.text(this, "", 13f, p.accent, Gravity.START); c.addView(step2Status)
            button(c, getString(R.string.step2_button)) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
        card().also { c ->
            title(c, getString(R.string.try_hint))
            val et = EditText(this).apply {
                setTextColor(p.label); setHintTextColor(p.hint); textSize = 18f; minLines = 2
                background = Ui.round(p.background, Ui.dp(this@MainActivity, 14f))
                setPadding(dp(14f), dp(10f), dp(14f), dp(10f)); gravity = Gravity.TOP or Gravity.START
            }
            c.addView(et, Ui.lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8f) })
        }
        card().also { c -> title(c, getString(R.string.tip_title)); body(c, getString(R.string.tip_body)) }

        buildUpdateCard()

        card().also { c ->
            title(c, getString(R.string.settings_title))
            choice(c, getString(R.string.pref_theme), listOf("auto" to R.string.theme_auto, "dark" to R.string.theme_dark, "light" to R.string.theme_light),
                { prefs.theme }, { prefs.theme = it })
            choice(c, getString(R.string.pref_language), listOf("ar" to R.string.lang_ar, "en" to R.string.lang_en),
                { prefs.lang }, { prefs.lang = it })
            slider(c)
            toggle(c, getString(R.string.pref_haptic), null, prefs.haptic) { prefs.haptic = it }
            toggle(c, getString(R.string.pref_sound), null, prefs.sound) { prefs.sound = it }
            toggle(c, getString(R.string.pref_popup), null, prefs.keyPopup) { prefs.keyPopup = it }
            toggle(c, getString(R.string.pref_digits), getString(R.string.pref_digits_desc), prefs.arabicDigits) { prefs.arabicDigits = it }
            toggle(c, getString(R.string.pref_autocap), null, prefs.autoCap) { prefs.autoCap = it }
            toggle(c, getString(R.string.pref_clipboard), getString(R.string.pref_clipboard_desc), prefs.clipboardHistory) { prefs.clipboardHistory = it }
        }

        card().also { c ->
            title(c, getString(R.string.about_title))
            body(c, getString(R.string.about_fonts))
            body(c, getString(R.string.trial_note), 0xFFE8A23B.toInt())
            body(c, getString(R.string.dubai_note), 0xFFE8A23B.toInt())
            val arabicUi = resources.configuration.locales[0].language == "ar"
            val names = FontRepository(this).builtInFonts().joinToString("  •  ") { it.displayName(arabicUi) }
            body(c, names)
        }
        content.addView(View(this), Ui.lp(1, dp(24f)))
    }

    private fun buildUpdateCard() {
        card().also { c ->
            title(c, getString(R.string.update_title))
            body(c, getString(R.string.update_current, updater.currentVersion))
            upStatus = Ui.text(this, "", 14f, p.accent, Gravity.START).apply { setPadding(0, dp(4f), 0, dp(4f)) }
            c.addView(upStatus)
            upNotes = Ui.text(this, "", 13f, p.hint, Gravity.START).apply { visibility = View.GONE; setLineSpacing(0f, 1.15f) }
            c.addView(upNotes)
            upProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
            c.addView(upProgress, Ui.lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(6f) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            upCheck = Ui.pill(this, getString(R.string.update_check), p.background, p.label, 14f)
            Ui.pressable(upCheck, { if (!busy) checkNow(true) })
            row.addView(upCheck, Ui.lp(Ui.WRAP, Ui.WRAP).apply { marginEnd = dp(8f) })
            upInstall = Ui.pill(this, getString(R.string.update_install), p.accent, p.onAccent, 14f).apply { visibility = View.GONE }
            Ui.pressable(upInstall, { pending?.let { if (!busy) installNow(it) } })
            row.addView(upInstall)
            c.addView(row, Ui.lp(Ui.WRAP, Ui.WRAP).apply { topMargin = dp(8f) })
            toggle(c, getString(R.string.update_auto), getString(R.string.update_auto_desc), prefs.autoUpdate) { on ->
                prefs.autoUpdate = on; updater.scheduleDaily()
                if (on && android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 5)
            }
            body(c, getString(R.string.update_privacy))
        }
        updater.scheduleDaily()
    }

    private fun showAvailable(info: UpdateInfo) {
        pending = info
        upStatus.text = getString(R.string.update_available, info.version)
        upNotes.text = info.notes; upNotes.visibility = if (info.notes.isEmpty()) View.GONE else View.VISIBLE
        upInstall.visibility = View.VISIBLE
    }

    private fun checkNow(manual: Boolean) {
        busy = true
        if (manual) { upStatus.text = getString(R.string.update_checking); upInstall.visibility = View.GONE; upNotes.visibility = View.GONE }
        Thread {
            val r = updater.check()
            main.post {
                busy = false
                when (r) {
                    is CheckResult.Available -> showAvailable(r.info)
                    is CheckResult.UpToDate -> if (manual) upStatus.text = getString(R.string.update_up_to_date, updater.currentVersion)
                    is CheckResult.Failed -> if (manual) upStatus.text = getString(R.string.update_failed, r.reason)
                }
            }
        }.start()
    }

    private fun installNow(info: UpdateInfo) {
        if (!updater.canInstall()) {
            upStatus.text = getString(R.string.update_allow_installs)
            startActivity(updater.unknownSourcesIntent()); return
        }
        busy = true; upInstall.visibility = View.GONE; upCheck.visibility = View.GONE
        upProgress.progress = 0; upProgress.visibility = View.VISIBLE
        upStatus.text = getString(R.string.update_downloading, 0)
        prefs.lastInstallError = ""
        Thread {
            val r = updater.download(info) { done, total ->
                if (total > 0) main.post { val pct = (done * 100 / total).toInt(); upProgress.progress = pct; upStatus.text = getString(R.string.update_downloading, pct) }
            }
            main.post {
                busy = false; upProgress.visibility = View.GONE; upCheck.visibility = View.VISIBLE
                r.onSuccess { f ->
                    upStatus.text = getString(R.string.update_confirm)
                    runCatching { updater.install(f) }.onFailure { upStatus.text = getString(R.string.update_install_error, it.message ?: it.javaClass.simpleName); upInstall.visibility = View.VISIBLE }
                }.onFailure {
                    upStatus.text = getString(R.string.update_download_failed, it.message ?: it.javaClass.simpleName); upInstall.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun choice(parent: LinearLayout, label: String, options: List<Pair<String, Int>>, get: () -> String, set: (String) -> Unit) {
        parent.addView(Ui.text(this, label, 14f, p.label, Gravity.START).apply { setPadding(0, dp(14f), 0, dp(6f)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pills = ArrayList<Pair<String, TextView>>()
        fun paint() = pills.forEach { (k, v) ->
            val on = k == get()
            v.background = Ui.round(if (on) p.accent else p.background, Ui.dp(this, 20f))
            v.setTextColor(if (on) p.onAccent else p.label)
        }
        options.forEach { (k, res) ->
            val v = Ui.pill(this, getString(res), p.background, p.label, 14f)
            Ui.pressable(v, { set(k); paint() })
            pills.add(k to v)
            row.addView(v, Ui.lp(Ui.WRAP, Ui.WRAP).apply { marginEnd = dp(8f) })
        }
        paint()
        parent.addView(row)
    }

    private fun slider(parent: LinearLayout) {
        parent.addView(Ui.text(this, getString(R.string.pref_height), 14f, p.label, Gravity.START).apply { setPadding(0, dp(14f), 0, dp(2f)) })
        val sb = SeekBar(this).apply {
            max = 45; progress = prefs.heightPercent - 80
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) prefs.heightPercent = 80 + v }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        parent.addView(sb, Ui.lp(Ui.MATCH, Ui.WRAP))
    }

    private fun toggle(parent: LinearLayout, label: String, desc: String?, value: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10f), 0, 0) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, label, 15f, p.label, Gravity.START))
        if (desc != null) col.addView(Ui.text(this, desc, 12f, p.hint, Gravity.START))
        row.addView(col, Ui.lp(0, Ui.WRAP, 1f))
        val sw = Switch(this).apply { isChecked = value; setOnCheckedChangeListener { _: CompoundButton, b: Boolean -> onChange(b) } }
        row.addView(sw)
        parent.addView(row)
    }

    private fun refreshStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        step1Status.text = (if (enabled) "✓ " else "• ") + getString(if (enabled) R.string.status_done else R.string.status_todo)
        step2Status.text = (if (current) "✓ " else "• ") + getString(if (current) R.string.status_done else R.string.status_todo)
    }
}
