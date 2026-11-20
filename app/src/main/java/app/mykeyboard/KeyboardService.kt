package app.mykeyboard

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.Toast
import android.icu.text.BreakIterator

class KeyboardService : InputMethodService(), KeyboardListener, PanelHost, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var fonts: FontRepository
    private lateinit var clip: ClipboardStore
    private var root: FrameLayout? = null
    private var kv: KeyboardView? = null
    private var panelHost: FrameLayout? = null
    private var palette: Palette = Palette.DARK
    private var currentPanel: Tool? = null
    private var fontPanel: FontPanel? = null

    private var lang = "ar"
    private var page = Page.LETTERS
    private var shift = Shift.OFF
    private var enterKind = EnterKind.ENTER
    private var latinOnlyField = false
    private var passwordField = false
    private var lastShiftTap = 0L
    private var inputTypeFull = 0

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this); fonts = FontRepository(this); clip = ClipboardStore(this)
        lang = prefs.lang
        runCatching { app.mykeyboard.update.UpdateManager(this).scheduleDaily() }   // local scheduling only, no network here
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    // ------------------------------------------------------------------ view
    override fun onCreateInputView(): View {
        palette = Palette.resolve(this, prefs.theme)
        val keyboard = KeyboardView(this, prefs).also {
            it.listener = this
            it.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val host = FrameLayout(this).apply { visibility = View.GONE; isClickable = true }
        val frame = FrameLayout(this)
        frame.addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = keyboard.toolbarH.toInt()
        })
        frame.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 35) {
                val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                kv?.extraBottom = nav.toFloat()
            }
            insets
        }
        root = frame; kv = keyboard; panelHost = host
        applyTheme(); applyTypeface(); applyLayout()
        return frame
    }

    private fun applyTheme() {
        palette = Palette.resolve(this, prefs.theme)
        kv?.setPalette(palette)
        root?.setBackgroundColor(palette.background)
        runCatching {
            val w = window?.window ?: return@runCatching
            if (Build.VERSION.SDK_INT < 35) w.navigationBarColor = palette.background
            if (Build.VERSION.SDK_INT >= 30) {
                w.insetsController?.setSystemBarsAppearance(
                    if (palette.dark) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                )
            }
        }
        val open = currentPanel
        if (open != null) { currentPanel = null; openPanel(open) }
    }

    private fun applyTypeface() {
        kv?.setTypeface(fonts.typeface(fonts.find(prefs.fontId), prefs.fontWeight))
    }

    private fun applyLayout() {
        kv?.setRtlUi(resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)
        kv?.setLayoutState(LayoutState(lang, page, shift, prefs.arabicDigits, enterKind))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
        when (key) {
            Prefs.K_FONT, Prefs.K_WEIGHT -> applyTypeface()
            Prefs.K_THEME -> applyTheme()
            Prefs.K_HEIGHT -> { kv?.refreshGeometry(); updatePanelMargin() }
            Prefs.K_DIGITS -> applyLayout()
            FONT_LIST_VERSION -> fontPanel?.refresh()
        }
    }

    private fun updatePanelMargin() {
        val k = kv ?: return
        (panelHost?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = k.toolbarH.toInt()
        panelHost?.requestLayout()
    }

    // ------------------------------------------------------------------ input session
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        closePanel()
        inputTypeFull = info.inputType
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        passwordField = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        latinOnlyField = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI) || passwordField
        lang = if (latinOnlyField || numeric) "en" else prefs.lang
        page = if (numeric) Page.SYM1 else Page.LETTERS
        shift = Shift.OFF
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val multiline = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        enterKind = if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 || multiline) EnterKind.ENTER else when (action) {
            EditorInfo.IME_ACTION_GO -> EnterKind.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterKind.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterKind.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterKind.DONE
            else -> EnterKind.ENTER
        }
        updateAutoCap()
        applyLayout()
    }

    override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(subtype)
        if (latinOnlyField) return
        val tag = if (Build.VERSION.SDK_INT >= 24) subtype.languageTag.ifEmpty { subtype.locale } else subtype.locale
        val newLang = if (tag.startsWith("ar")) "ar" else "en"
        if (newLang != lang) { lang = newLang; prefs.lang = newLang; page = Page.LETTERS; shift = Shift.OFF; applyLayout() }
    }

    private fun updateAutoCap() {
        if (lang != "en" || page != Page.LETTERS || shift == Shift.LOCK) return
        if (!prefs.autoCap || passwordField) { if (shift == Shift.ON) shift = Shift.OFF; return }
        val caps = currentInputConnection?.getCursorCapsMode(inputTypeFull) ?: 0
        shift = if (caps != 0) Shift.ON else Shift.OFF
    }

    // ------------------------------------------------------------------ keys
    override fun onKey(action: Action, text: String) {
        val ic = currentInputConnection
        when (action) {
            Action.TEXT, Action.COMMA, Action.PERIOD -> {
                ic?.commitText(text, 1)
                if (shift == Shift.ON && lang == "en" && page == Page.LETTERS) shift = Shift.OFF
                updateAutoCap(); applyLayout()
            }
            Action.SPACE -> { ic?.commitText(" ", 1); updateAutoCap(); applyLayout() }
            Action.BACKSPACE -> { backspace(); updateAutoCap(); applyLayout() }
            Action.ENTER -> { sendEnter(); updateAutoCap(); applyLayout() }
            Action.SHIFT -> {
                val now = System.currentTimeMillis()
                shift = when {
                    shift == Shift.LOCK -> Shift.OFF
                    now - lastShiftTap < 350 -> Shift.LOCK
                    shift == Shift.ON -> Shift.OFF
                    else -> Shift.ON
                }
                lastShiftTap = now; applyLayout()
            }
            Action.SYMBOLS -> { page = Page.SYM1; applyLayout() }
            Action.SYMBOLS2 -> { page = Page.SYM2; applyLayout() }
            Action.LETTERS -> { page = Page.LETTERS; updateAutoCap(); applyLayout() }
            Action.LANG -> {
                lang = if (lang == "ar") "en" else "ar"
                if (!latinOnlyField) prefs.lang = lang
                page = Page.LETTERS; shift = Shift.OFF; updateAutoCap(); applyLayout()
            }
        }
    }

    override fun onLongAction(action: Action) {
        when (action) {
            Action.SPACE, Action.LANG -> showImePicker()
            Action.COMMA -> openPanel(Tool.EMOJI)
            else -> {}
        }
    }

    override fun onTool(tool: Tool) {
        when (tool) {
            Tool.MIC -> startVoice()
            Tool.SETTINGS -> openSettings()
            else -> if (currentPanel == tool) closePanel() else openPanel(tool)
        }
    }

    private fun sendEnter() {
        val ic = currentInputConnection ?: return
        if (enterKind == EnterKind.ENTER) { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER); return }
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        ic.performEditorAction(action)
    }

    override fun backspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(16, 0)
        if (before.isNullOrEmpty()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        ic.deleteSurroundingText(lastUnitLength(before.toString()), 0)
    }

    override fun insert(text: String) { currentInputConnection?.commitText(text, 1) }

    // ------------------------------------------------------------------ panels
    override fun openPanel(tool: Tool) {
        val host = panelHost ?: return
        val view: View = when (tool) {
            Tool.FONTS -> FontPanel(this, palette, prefs, fonts, this).also { fontPanel = it }
            Tool.EMOJI -> EmojiPanel(this, palette, prefs, this)
            Tool.CLIPBOARD -> ClipboardPanel(this, palette, prefs, clip, this)
            Tool.MENU -> MenuPanel(this, palette, prefs, this)
            else -> return
        }
        if (tool != Tool.FONTS) fontPanel = null
        host.removeAllViews()
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.visibility = View.VISIBLE
        currentPanel = tool
        kv?.activeTool = tool
    }

    override fun closePanel() {
        panelHost?.let { it.visibility = View.GONE; it.removeAllViews() }
        fontPanel = null
        currentPanel = null
        kv?.activeTool = null
    }

    override fun fontChanged() = applyTypeface()

    override fun toast(resId: Int) { Toast.makeText(this, resId, Toast.LENGTH_SHORT).show() }

    override fun cycleTheme() {
        val next = when (prefs.theme) { "auto" -> "dark"; "dark" -> "light"; else -> "auto" }
        prefs.theme = next
        toast(when (next) { "dark" -> R.string.toast_theme_dark; "light" -> R.string.toast_theme_light; else -> R.string.toast_theme_auto })
    }

    override fun showImePicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openFontImport() {
        startActivity(Intent(this, FontImportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun startVoice() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        for (imi in imm.enabledInputMethodList) {
            if (imi.packageName == packageName) continue
            for (st in imm.getEnabledInputMethodSubtypeList(imi, true)) {
                if (st.mode.equals("voice", ignoreCase = true)) {
                    if (Build.VERSION.SDK_INT >= 28) switchInputMethod(imi.id, st)
                    else @Suppress("DEPRECATION") imm.setInputMethodAndSubtype(window.window!!.attributes.token, imi.id, st)
                    return
                }
            }
        }
        toast(R.string.toast_no_voice)
    }

    private fun captureClipboard() {
        if (!prefs.clipboardHistory || passwordField) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val c = cm.primaryClip ?: return
        if (c.itemCount == 0) return
        if (Build.VERSION.SDK_INT >= 33 && c.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
        runCatching { c.getItemAt(0).coerceToText(this)?.toString() }.getOrNull()?.let { clip.add(it) }
    }

    companion object {
        const val FONT_LIST_VERSION = "font_list_version"

        /** Length (in UTF-16 units) of what one backspace should remove. Keeps Arabic marks and emoji sequences sane. */
        fun lastUnitLength(before: String): Int {
            if (before.isEmpty()) return 1
            val cp = before.codePointBefore(before.length)
            // Arabic harakat / combining marks are deleted on their own so the base letter stays
            if (cp in 0x064B..0x065F || cp == 0x0670) return Character.charCount(cp)
            val it = BreakIterator.getCharacterInstance()
            it.setText(before)
            val start = it.preceding(before.length)
            return (before.length - start).coerceAtLeast(1)
        }
    }
}
