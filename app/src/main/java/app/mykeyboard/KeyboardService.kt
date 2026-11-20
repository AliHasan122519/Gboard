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
import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import app.mykeyboard.text.Correction
import app.mykeyboard.text.LearnedWords
import app.mykeyboard.text.SuggestionEngine
import app.mykeyboard.text.TextStyle
import app.mykeyboard.text.WordDict

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

    // typing assistance
    private var engine: SuggestionEngine? = null
    private var suggestOk = true
    private var currentSuggestions: List<String> = emptyList()
    private var lastCorrection: Correction? = null
    private var lastBoundary = ""
    private var styleList: List<TextStyle>? = null
    private var pendingVoice: String? = null
    private var imageCapable = false
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshTask = Runnable { refreshSuggestions() }
    /** While deleting fast, suggestions are refreshed once the burst settles instead of on every character. */
    private fun scheduleRefresh() { refreshHandler.removeCallbacks(refreshTask); refreshHandler.postDelayed(refreshTask, 60) }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this); fonts = FontRepository(this); clip = ClipboardStore(this)
        lang = prefs.lang
        loadDictionaries()
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
    override fun onCreateInputView(): View = try { buildInputView() } catch (t: Throwable) {
        CrashReporter.record(this, "KeyboardService.onCreateInputView", t)
        android.widget.TextView(this).apply { text = "Keyboard error: ${t.javaClass.simpleName}"; setPadding(40, 40, 40, 40); setBackgroundColor(0xFF1C1E2A.toInt()); setTextColor(0xFFFF6B6B.toInt()) }
    }

    private fun buildInputView(): View {
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
            Prefs.K_VOICE_TS -> { pendingVoice = prefs.sp.getString(Prefs.K_VOICE_TEXT, null); commitPendingVoice() }
            Prefs.K_SUGGEST, Prefs.K_AUTOCORRECT, Prefs.K_STYLE -> refreshSuggestions()
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
        try { startInput(info) } catch (t: Throwable) { CrashReporter.record(this, "KeyboardService.onStartInputView", t) }
    }

    private fun startInput(info: EditorInfo) {
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
        suggestOk = !(passwordField || latinOnlyField || numeric || info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        lastCorrection = null
        imageCapable = info.contentMimeTypes?.any { it == "image/png" || it == "image/*" } == true
        if (prefs.imageSend && imageCapable) enterKind = EnterKind.SEND          // the send key shows it will send a picture
        updateAutoCap()
        applyLayout()
        refreshSuggestions()
        if (pendingVoice == null) pendingVoice = prefs.sp.getString(Prefs.K_VOICE_TEXT, null)
        if (pendingVoice != null) Handler(Looper.getMainLooper()).postDelayed({ commitPendingVoice() }, 250)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        kv?.setSuggestions(emptyList())
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        scheduleRefresh()
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
        try { handleKey(action, text) } catch (t: Throwable) { CrashReporter.record(this, "KeyboardService.onKey($action)", t) }
    }

    private fun isBoundary(action: Action, text: String): Boolean = when (action) {
        Action.SPACE, Action.ENTER, Action.PERIOD, Action.COMMA -> true
        Action.TEXT -> text.isNotEmpty() && !text[0].isLetterOrDigit() && text[0] != '\'' && text[0] != '’'
        else -> false
    }

    private fun activeStyle(): TextStyle? = prefs.textStyle.takeIf { it != "none" }?.let { id -> styles().firstOrNull { it.id == id } }

    private fun styles(): List<TextStyle> = styleList ?: runCatching {
        TextStyle.load(assets.open("styles.json").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList()).also { styleList = it }

    private fun styled(t: String): String = activeStyle()?.apply(t) ?: t

    private fun assistOn() = suggestOk && prefs.suggestions && page == Page.LETTERS && prefs.textStyle == "none"

    /** At a word boundary: fix the word just typed (or remember it) before the boundary character goes in. */
    private fun beforeBoundary(): Correction? {
        val e = engine ?: return null
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(40, 0) ?: return null
        if (!assistOn()) return null
        val corr = e.capitalizeI(before, lang) ?: if (prefs.autocorrect) e.autoCorrect(before, lang) else null
        if (corr != null) {
            ic.beginBatchEdit(); ic.deleteSurroundingText(corr.original.length, 0); ic.commitText(corr.replacement, 1); ic.endBatchEdit()
        } else e.learn(e.currentWord(before, lang), lang)
        return corr
    }

    private fun revertCorrection(): Boolean {
        val lc = lastCorrection ?: return false
        val ic = currentInputConnection ?: return false
        lastCorrection = null
        val tail = lc.replacement + lastBoundary
        if (ic.getTextBeforeCursor(tail.length, 0)?.toString() != tail) return false
        ic.beginBatchEdit(); ic.deleteSurroundingText(tail.length, 0); ic.commitText(lc.original, 1); ic.endBatchEdit()
        engine?.rejectCorrection(lc.original)
        return true
    }

    private fun refreshSuggestions() {
        val e = engine
        currentSuggestions = if (e == null || !assistOn()) emptyList()
        else e.suggest(currentInputConnection?.getTextBeforeCursor(40, 0) ?: "", lang)
        kv?.setSuggestions(currentSuggestions)
    }

    /** One cheap round-trip per character while the delete key is held; the rest is settled once the burst ends. */
    private fun fastDelete() {
        val ic = currentInputConnection ?: return
        lastCorrection = null
        if (!ic.deleteSurroundingTextInCodePoints(1, 0)) ic.deleteSurroundingText(1, 0)
        scheduleSettle()
    }

    private val settleTask = Runnable { updateAutoCap(); applyLayout(); refreshSuggestions() }
    private fun scheduleSettle() { refreshHandler.removeCallbacks(settleTask); refreshHandler.postDelayed(settleTask, 90) }

    private fun handleKey(action: Action, text: String) {
        if (action == Action.BACKSPACE && text == "hold") { fastDelete(); return }
        val ic = currentInputConnection
        if (action != Action.BACKSPACE) lastCorrection = null
        val boundary = isBoundary(action, text)
        val corr = if (boundary && action != Action.ENTER) beforeBoundary() else if (boundary) { beforeBoundary(); null } else null
        // stretched-letter style: a trailing tatweel must not stay at the end of a word
        if (boundary && activeStyle()?.kashida == true && ic?.getTextBeforeCursor(1, 0)?.toString() == "\u0640") ic.deleteSurroundingText(1, 0)
        when (action) {
            Action.TEXT, Action.COMMA, Action.PERIOD -> {
                ic?.commitText(activeStyle()?.typed(text) ?: text, 1)
                if (shift == Shift.ON && lang == "en" && page == Page.LETTERS) shift = Shift.OFF
                if (corr != null) { lastCorrection = corr; lastBoundary = text }
                updateAutoCap(); applyLayout()
            }
            Action.SPACE -> { ic?.commitText(" ", 1); if (corr != null) { lastCorrection = corr; lastBoundary = " " }; updateAutoCap(); applyLayout() }
            Action.BACKSPACE -> { if (!revertCorrection()) backspace(); updateAutoCap(); applyLayout() }
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
        if (action == Action.BACKSPACE) scheduleRefresh() else refreshSuggestions()
    }

    override fun onSuggestion(index: Int) {
        try {
            val text = currentSuggestions.getOrNull(index) ?: return
            val e = engine ?: return
            val ic = currentInputConnection ?: return
            val w = e.currentWord(ic.getTextBeforeCursor(40, 0) ?: "", lang)
            ic.beginBatchEdit(); ic.deleteSurroundingText(w.length, 0); ic.commitText("$text ", 1); ic.endBatchEdit()
            if (text == w) e.rejectCorrection(w)              // the user insisted on the word as typed
            lastCorrection = null
            updateAutoCap(); applyLayout(); refreshSuggestions()
        } catch (t: Throwable) { CrashReporter.record(this, "KeyboardService.onSuggestion", t) }
    }

    override fun onStatusTap() {}

    override fun onLongAction(action: Action) {
        when (action) {
            Action.BACKSPACE -> { deleteWord(); scheduleSettle() }
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
        if (prefs.imageSend && imageCapable) { sendTextAsImage(); return }
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

    /** Hold-to-delete, second stage: removes the word before the cursor (or the selection). */
    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        lastCorrection = null
        if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: ""
        if (before.isEmpty()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        ic.deleteSurroundingText(wordDeleteLength(before), 0)
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
        val probe = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        if (packageManager.resolveActivity(probe, 0) == null) { legacyVoiceIme(); return }
        startActivity(Intent(this, VoiceDialogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("lang", lang))
    }

    /** The speech dialog returns after the text field restarts, so the text waits here until the keyboard is active again. */
    private fun commitPendingVoice() {
        val t = pendingVoice ?: return
        val ic = currentInputConnection ?: return
        ic.commitText(styled(t) + " ", 1)
        pendingVoice = null
        prefs.sp.edit().remove(Prefs.K_VOICE_TEXT).apply()
        refreshSuggestions()
    }

    /** No speech service on the device: hand over to another keyboard's voice mode if one is enabled. */
    private fun legacyVoiceIme() {
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

    private fun loadDictionaries() {
        val appPrefs = prefs
        Thread {
            val dicts = HashMap<String, WordDict>()
            for (l in listOf("ar", "en")) runCatching {
                val d = WordDict(l)
                assets.open("dict/$l.txt").bufferedReader().useLines { d.load(it) }
                dicts[l] = d
            }
            val learned = LearnedWords(
                { appPrefs.sp.getString(Prefs.K_LEARNED, "") ?: "" },
                { appPrefs.sp.edit().putString(Prefs.K_LEARNED, it).apply() },
            )
            Handler(Looper.getMainLooper()).post { engine = SuggestionEngine(dicts, learned); refreshSuggestions() }
        }.start()
    }

    // ------------------------------------------------------------------ message font: styles + image
    private fun fullFieldText(ic: InputConnection): String {
        val ex = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()
        if (!ex.isNullOrEmpty()) return ex
        return (ic.getTextBeforeCursor(5000, 0) ?: "").toString() + (ic.getTextAfterCursor(5000, 0) ?: "").toString()
    }

    private fun clearField(ic: InputConnection) {
        val b = ic.getTextBeforeCursor(5000, 0)?.length ?: 0
        val a = ic.getTextAfterCursor(5000, 0)?.length ?: 0
        ic.deleteSurroundingText(b, a)
    }

    override fun styleChanged() { refreshSuggestions() }

    override fun applyStyleToField() {
        val ic = currentInputConnection ?: return
        val st = activeStyle() ?: run { toast(R.string.style_pick_first); return }
        val text = fullFieldText(ic)
        if (text.isEmpty()) { toast(R.string.img_empty); return }
        ic.beginBatchEdit(); clearField(ic); ic.commitText(st.apply(text), 1); ic.endBatchEdit()
    }

    /** Renders the message in the selected font and sends it as an image (or copies it if the app cannot take images). */
    override fun sendTextAsImage() {
        val ic = currentInputConnection ?: return
        val text = fullFieldText(ic)
        if (text.isBlank()) { toast(R.string.img_empty); return }
        try {
            val tf = fonts.typeface(fonts.find(prefs.fontId), prefs.fontWeight)
            val file = TextImage.render(this, text, tf, TextImage.PRESETS[prefs.imagePreset])
            val uri = TextImage.uriFor(this, file)
            val types = currentInputEditorInfo?.contentMimeTypes
            val accepts = types?.any { it == "image/png" || it == "image/*" || it == "image/gif" } == true
            val sent = accepts && ic.commitContent(
                InputContentInfo(uri, ClipDescription("message", arrayOf("image/png"))),
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null,
            )
            if (sent) { clearField(ic); closePanel(); toast(R.string.img_sent) }
            else {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newUri(contentResolver, "message", uri))
                toast(R.string.img_copied)
            }
        } catch (t: Throwable) {
            CrashReporter.record(this, "KeyboardService.sendTextAsImage", t); toast(R.string.img_failed)
        }
    }

    override fun openStylePanel() {
        val host = panelHost ?: return
        val view = StylePanel(this, palette, prefs, this, styles())
        host.removeAllViews()
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.visibility = View.VISIBLE
        currentPanel = Tool.FONTS
        fontPanel = null
        kv?.activeTool = Tool.FONTS
    }

    private fun captureClipboard() {
        if (!prefs.clipboardHistory || passwordField) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val c = cm.primaryClip ?: return
        if (c.itemCount == 0) return
        if (c.description.hasMimeType("image/*")) return      // our own rendered messages, not text
        if (Build.VERSION.SDK_INT >= 33 && c.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
        runCatching { c.getItemAt(0).coerceToText(this)?.toString() }.getOrNull()?.let { clip.add(it) }
    }

    companion object {
        private fun isWordPart(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '_' || Character.getType(c) == Character.NON_SPACING_MARK.toInt()

        /** How many UTF-16 units to remove to delete the word (or the run of symbols) before the cursor, with trailing spaces. */
        fun wordDeleteLength(before: String): Int {
            var i = before.length
            while (i > 0 && before[i - 1].isWhitespace()) i--
            if (i > 0) {
                val word = isWordPart(before[i - 1])
                while (i > 0 && !before[i - 1].isWhitespace() && isWordPart(before[i - 1]) == word) i--
            }
            if (i in 1 until before.length && Character.isLowSurrogate(before[i]) && Character.isHighSurrogate(before[i - 1])) i--
            return (before.length - i).coerceAtLeast(1)
        }

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
