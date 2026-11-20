package app.mykeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class Tool { MIC, SETTINGS, FONTS, CLIPBOARD, EMOJI, MENU }

interface KeyboardListener {
    fun onKey(action: Action, text: String)
    fun onLongAction(action: Action)
    fun onTool(tool: Tool)
    fun onSuggestion(index: Int)
    fun onStatusTap()
}

/**
 * The whole typing surface (tool strip + key rows + key popups) drawn on one canvas.
 * Geometry is measured from the reference design; keys are laid out left-to-right in both languages,
 * exactly like the reference keyboard does.
 */
class KeyboardView(
    context: Context,
    private val prefs: Prefs,
) : View(context) {

    var listener: KeyboardListener? = null
    private var palette: Palette = Palette.DARK
    /** The chosen font: used for the letters only. */
    private var typeface: Typeface = Typeface.DEFAULT
    /** Fixed system face for everything that is not a letter (hints, space label, pill labels, comma, period). */
    private val uiFace: Typeface = Typeface.DEFAULT
    private var state = LayoutState("ar")
    private var rows: List<Row> = emptyList()
    private var rtlUi = false
    /** Word suggestions shown in place of the tool icons while typing (empty = show the tools). */
    private var suggestions: List<String> = emptyList()
    /** Non-null while a status line (dictation) replaces the tool strip. */
    private var status: String? = null
    private var pressedStrip = -1
    fun setSuggestions(list: List<String>) { if (list != suggestions) { suggestions = list; invalidate() } }
    fun setStatus(s: String?) { if (s != status) { status = s; invalidate() } }
    var activeTool: Tool? = null
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmpRect = RectF()
    private val bounds = Rect()
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // geometry (px)
    /**
     * Everything is proportional to the keyboard width, exactly like the reference design (1079 px wide:
     * toolbar 157, keys 121, row gap 30, bottom padding 41), so the keyboard has the same proportions on every phone.
     * In landscape the scale is capped so the keyboard never takes more than about half of the screen.
     */
    private val u: Float get() {
        val w = (if (width > 0) width else resources.displayMetrics.widthPixels).toFloat()
        val cap = resources.displayMetrics.heightPixels * 0.46f / 777f
        return min(w / 1079f, cap)
    }
    val toolbarH get() = 157f * u
    private val keyH get() = 121f * u * prefs.heightPercent / 100f
    private val rowGap get() = 30f * u * (0.6f + 0.4f * prefs.heightPercent / 100f)
    private val bottomPad get() = 41f * u
    var extraBottom = 0f
        set(v) { if (field != v) { field = v; requestLayout() } }

    fun contentHeightPx(): Int =
        (toolbarH + 4 * keyH + 3 * rowGap + (keyH * 0.03f) + bottomPad + extraBottom).toInt()

    // ---- state setters ----
    fun setPalette(p: Palette) { palette = p; invalidate() }
    fun setTypeface(tf: Typeface) { typeface = tf; refCache.clear(); sizeCache.clear(); bottomCache.clear(); invalidate() }
    fun setLayoutState(s: LayoutState) {
        val structural = s.lang != state.lang || s.page != state.page
        val old = rows
        state = s; rows = Layouts.rows(s); layoutKeys()
        if (structural) cancelAll() else remapPointers(old)      // shift / enter-key refreshes keep fingers that are down alive
        invalidate()
    }

    private fun remapPointers(old: List<Row>) {
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i); val k = p.key ?: continue
            for (r in old.indices) {
                val c = old[r].keys.indexOfFirst { it === k }
                if (c >= 0) { p.key = rows.getOrNull(r)?.keys?.getOrNull(c) ?: k; break }
            }
        }
    }
    fun setRtlUi(rtl: Boolean) { rtlUi = rtl; invalidate() }
    fun refreshGeometry() { requestLayout(); layoutKeys(); invalidate() }

    // ---- layout ----
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), contentHeightPx())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { layoutKeys() }

    private fun layoutKeys() {
        if (width <= 0) return
        KeyGeometry.layout(rows, width.toFloat(), toolbarH, keyH, rowGap)
    }

    // ---- drawing ----
    private val refCache = HashMap<String, Float>()
    private val sizeCache = HashMap<String, Float>()
    private val bottomCache = HashMap<String, Float>()

    /**
     * Text size that makes the chosen font's alef (Arabic) / capital H (Latin) exactly as tall as in the reference design,
     * so every font fills the keys the same way and only the letter *shapes* differ.
     */
    private fun letterSize(arabic: Boolean, keyH: Float): Float {
        val keyW = keyH * 83f / 121f * (if (arabic) 1f else 1.17f)      // key width at this height (English keys are wider)
        val key = "${if (arabic) "a" else "l"}|${keyH.toInt()}"
        return sizeCache.getOrPut(key) {
            val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = this@KeyboardView.typeface; textSize = 100f }
            val ref = if (arabic) "ا" else "H"
            probe.getTextBounds(ref, 0, ref.length, bounds)
            val h = (bounds.bottom - bounds.top).toFloat().coerceAtLeast(1f)
            val target = keyH * if (arabic) ALEF_H else CAP_H
            var size = (target * 100f / h).coerceIn(keyH * 0.18f, keyH * 0.9f)
            val bottom = bounds.bottom * size / 100f
            // the widest letter of the font must stay inside its key (the reference's widest letter fills 85 % of a key)
            val letters = if (arabic) "ضصثقفغعهخحجشسيبلاتنمكطذءؤرىةوزظد" else "mwMWQO"
            var widest = 1f
            for (ch in letters) { probe.getTextBounds(ch.toString(), 0, 1, bounds); widest = max(widest, (bounds.right - bounds.left).toFloat()) }
            val maxW = keyW * 0.855f
            if (widest * size / 100f > maxW) size = maxW * 100f / widest
            bottomCache[key] = bottom * (size / ((target * 100f / h).coerceIn(keyH * 0.18f, keyH * 0.9f)))
            size
        }
    }

    private fun letterBottom(arabic: Boolean, keyH: Float): Float {
        letterSize(arabic, keyH)
        return bottomCache["${if (arabic) "a" else "l"}|${keyH.toInt()}"] ?: 0f
    }

    /** Baseline that visually centres a reference string vertically on [cy], so all keys share one baseline. */
    private fun baselineFor(p: Paint, cy: Float, latin: Boolean): Float {
        val key = "${System.identityHashCode(p.typeface)}|${p.textSize}|$latin"
        val off = refCache.getOrPut(key) {
            val ref = if (latin) "xh" else "لجا"
            p.getTextBounds(ref, 0, ref.length, bounds)
            (bounds.top + bounds.bottom) / 2f
        }
        return cy - off
    }

    private fun isLatin(s: String) = s.isNotEmpty() && s[0].code < 0x250

    private fun setText(size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER, face: Typeface = uiFace) {
        text.typeface = face; text.textSize = size; text.color = color; text.textAlign = align
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(palette.background)
        drawToolbar(c)
        val ar = state.lang == "ar"
        for (row in rows) for (k in row.keys) {
            try { drawKey(c, k, ar) } catch (t: Throwable) { CrashReporter.record(context, "KeyboardView.drawKey('${k.label}')", t) }
        }
        try { drawPopups(c) } catch (t: Throwable) { CrashReporter.record(context, "KeyboardView.drawPopups", t) }
    }

    private fun toolCenters(): FloatArray {
        val w = width.toFloat()
        return floatArrayOf(68f, 257f, 445f, 633f, 820f, 1010f).map { it / 1079f * w }.toFloatArray()
    }

    private fun stripBounds(): Pair<Float, Float> {
        val m = KeyGeometry.margin(width.toFloat()); return m to (width - m)
    }

    private fun drawStrip(c: Canvas) {
        val cy = 67f * u
        val (l, r) = stripBounds()
        val st = status
        if (st != null) {
            // dictation: accent mic circle + live text
            fillPaint.color = palette.accent
            c.drawCircle(l + 51f * u, cy, 51f * u, fillPaint)
            Icons.draw(c, Icon.MIC, l + 51f * u, cy, 52f * u, palette.onAccent)
            setText(44f * u, palette.label, Paint.Align.LEFT)
            val avail = r - l - 150f * u
            val shown = android.text.TextUtils.ellipsize(st, android.text.TextPaint(text), avail, android.text.TextUtils.TruncateAt.START).toString()
            text.textAlign = Paint.Align.LEFT
            c.drawText(shown, l + 130f * u, baselineFor(text, cy, isLatin(shown)), text)
            return
        }
        val n = 3
        val cw = (r - l) / n
        for (i in 0 until n) {
            val sug = suggestions.getOrNull(i) ?: continue
            val x0 = l + i * cw
            if (pressedStrip == i) {
                tmpRect.set(x0 + 8f * u, 16f * u, x0 + cw - 8f * u, 118f * u)
                fillPaint.color = palette.keyPressed; c.drawRoundRect(tmpRect, 30f * u, 30f * u, fillPaint)
            }
            setText(letterSize(!isLatin(sug), keyH) * 0.75f, if (i == 0) palette.label else palette.hint, face = typeface)
            val fit = android.text.TextUtils.ellipsize(sug, android.text.TextPaint(text), cw - 36f * u, android.text.TextUtils.TruncateAt.END).toString()
            c.drawText(fit, x0 + cw / 2f, baselineFor(text, cy, isLatin(fit)), text)
            if (i < n - 1 && suggestions.size > i + 1) {
                fillPaint.color = palette.divider
                c.drawRect(x0 + cw - 1.5f * u, 36f * u, x0 + cw + 1.5f * u, 98f * u, fillPaint)
            }
        }
    }

    private fun drawToolbar(c: Canvas) {
        if (status != null || suggestions.isNotEmpty()) { drawStrip(c); return }
        val cy = 67f * u
        val xs = toolCenters()
        val icons = arrayOf(Icon.MIC, Icon.GEAR, Icon.PALETTE, Icon.CLIPBOARD, Icon.STICKER, Icon.GRID)
        val tools = Tool.values()
        for (i in icons.indices) {
            val pressedTool = pressedTool == tools[i]
            val active = activeTool == tools[i]
            if (i == 0 || active || pressedTool) {
                fillPaint.color = if (active) palette.funcKey else if (pressedTool) palette.keyPressed else palette.key
                c.drawCircle(xs[i], cy, 51f * u, fillPaint)
            }
            Icons.draw(c, icons[i], xs[i], cy, (if (i == 0) 52f else 58f) * u, if (active) palette.funcLabel else palette.label)
        }
    }

    private fun keyColor(k: Key, pressed: Boolean): Int {
        if (k.action == Action.ENTER && state.enter != EnterKind.ENTER) return if (pressed) palette.popupSelected else palette.accent
        if (k.action == Action.SHIFT && state.shift == Shift.LOCK) return palette.accent
        return when (k.style) {
            Style.NORMAL -> if (pressed) palette.keyPressed else palette.key
            else -> if (pressed) palette.funcKeyPressed else palette.funcKey
        }
    }

    private fun drawKey(c: Canvas, k: Key, ar: Boolean) {
        val pressed = (0 until pointers.size()).any { pointers.valueAt(it).key === k }
        val radius = if (k.style == Style.ACCENT || k.action == Action.ENTER) k.h / 2f else k.h * 0.19f
        tmpRect.set(k.x, k.y, k.x + k.w, k.y + k.h)
        fillPaint.color = keyColor(k, pressed)
        c.drawRoundRect(tmpRect, radius, radius, fillPaint)

        val cx = k.x + k.w / 2f
        val cy = k.y + k.h / 2f
        val labelColor = when {
            k.action == Action.ENTER && state.enter != EnterKind.ENTER -> palette.onAccent
            k.action == Action.SHIFT && state.shift == Shift.LOCK -> palette.onAccent
            k.style != Style.NORMAL -> palette.funcLabel
            else -> palette.label
        }
        when (k.action) {
            Action.BACKSPACE -> Icons.draw(c, Icon.BACKSPACE, cx, cy, k.h * 0.40f, labelColor)
            Action.SHIFT -> Icons.draw(
                c,
                when (state.shift) { Shift.OFF -> Icon.SHIFT; Shift.ON -> Icon.SHIFT_FILLED; Shift.LOCK -> Icon.SHIFT_LOCK },
                cx, cy, k.h * 0.40f, labelColor,
            )
            Action.LANG -> Icons.draw(c, Icon.GLOBE, cx, cy, k.h * 0.42f, labelColor)
            Action.ENTER -> Icons.draw(
                c,
                when (state.enter) {
                    EnterKind.ENTER -> Icon.ENTER; EnterKind.SEARCH -> Icon.SEARCH; EnterKind.SEND -> Icon.SEND
                    EnterKind.DONE -> Icon.CHECK; EnterKind.GO, EnterKind.NEXT -> Icon.ARROW_NEXT; EnterKind.IMAGE -> Icon.IMAGE
                },
                cx, cy, k.h * 0.42f, labelColor, rtl = state.lang == "ar",
            )
            Action.SPACE -> {
                setText(k.h * 0.34f, palette.funcLabel)
                c.drawText(k.label, cx, baselineFor(text, cy, false), text)
            }
            Action.COMMA, Action.PERIOD -> drawTwoLine(c, k, cx, labelColor)
            Action.SYMBOLS, Action.LETTERS, Action.SYMBOLS2 -> {
                setText(k.h * if (k.label.length > 3 && !k.ltrLabel) 0.34f else 0.36f, labelColor)
                if (k.ltrLabel) c.drawTextRun(k.label, 0, k.label.length, 0, k.label.length, cx, baselineFor(text, cy, false), false, text)
                else c.drawText(k.label, cx, baselineFor(text, cy, isLatin(k.label)), text)
            }
            Action.TEXT -> {
                val latin = isLatin(k.label)
                var size = letterSize(!latin, k.h)
                if (k.label.length > 1 && !latin) size *= 0.82f          // "لا" style ligatures
                setText(size, labelColor, face = typeface)
                // fixed baseline (measured from the reference: 72.7 % of the key height for Arabic)
                c.drawText(k.label, cx, k.y + k.h * (if (latin) BASE_LATIN else BASE_ARABIC) - letterBottom(!latin, k.h), text)
                k.hint?.let {
                    setText(k.h * 0.22f, palette.hint, Paint.Align.RIGHT)
                    c.drawText(it, k.x + k.w - k.w * 0.13f, k.y + k.h * 0.27f, text)
                }
            }
        }
    }

    private fun drawTwoLine(c: Canvas, k: Key, cx: Float, color: Int) {
        if (k.top == "☺") Icons.draw(c, Icon.SMILEY, cx, k.y + k.h * 0.27f, k.h * 0.22f, color)
        else if (k.top != null) {
            setText(k.h * 0.30f, palette.hint)
            c.drawText(k.top, cx, k.y + k.h * 0.36f, text)
        }
        setText(k.h * 0.50f, color)
        val base = if (k.top != null) k.y + k.h * 0.80f else baselineFor(text, k.y + k.h / 2f, false)
        c.drawText(k.label, cx, base, text)
    }

    // ---- popups ----
    private class Ptr(val id: Int, var key: Key?, val tool: Tool?, val x0: Float, val y0: Float) {
        var longPressed = false
        var alts: List<String> = emptyList()
        var selected = 0
        val grid = RectF()
        var cols = 1
        var cellW = 0f
        var cellH = 0f
        var repeating = false
        var consumed = false
        /** >= 0: suggestion index, -2: dictation status line, -1: not a strip touch. */
        var strip = -1
    }

    private val pointers = SparseArray<Ptr>()
    private var pressedTool: Tool? = null

    private fun altLabel(a: String) = if (Layouts.isTashkeel(a)) Layouts.DOTTED + a else a

    private fun drawPopups(c: Canvas) {
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            val k = p.key ?: continue
            if (p.longPressed && p.alts.isNotEmpty()) drawAltPopup(c, p, k)
            else if (prefs.keyPopup && k.action == Action.TEXT && k.label.isNotEmpty()) drawPreview(c, k)
        }
    }

    private fun drawPreview(c: Canvas, k: Key) {
        val w = k.w * 1.2f
        val h = k.h * 1.25f
        var left = k.x + k.w / 2f - w / 2f
        left = left.coerceIn(dp(2f), width - w - dp(2f))
        val top = max(dp(2f), k.y - h - dp(2f))
        tmpRect.set(left, top, left + w, top + h)
        fillPaint.color = palette.popup
        c.drawRoundRect(tmpRect, dp(10f), dp(10f), fillPaint)
        val latin = isLatin(k.label)
        setText(letterSize(!latin, k.h) * 1.45f, palette.label, face = typeface)
        c.drawText(k.label, tmpRect.centerX(), baselineFor(text, tmpRect.centerY(), latin), text)
    }

    private fun prepareAltGrid(p: Ptr, k: Key) {
        val n = p.alts.size
        val cols = if (n <= 7) n else ceil(n / 2f).toInt().coerceAtMost(8)
        val rowsN = ceil(n / cols.toFloat()).toInt()
        val cw = max(k.w * 0.95f, dp(40f))
        val ch = k.h * 1.0f
        val gw = cols * cw
        val gh = rowsN * ch
        var left = k.x + k.w / 2f - gw / 2f
        left = left.coerceIn(dp(2f), width - gw - dp(2f))
        val top = max(dp(2f), k.y - gh - dp(4f))
        p.grid.set(left, top, left + gw, top + gh)
        p.cols = cols; p.cellW = cw; p.cellH = ch
    }

    private fun drawAltPopup(c: Canvas, p: Ptr, k: Key) {
        val g = p.grid
        fillPaint.color = palette.popup
        c.drawRoundRect(g, dp(12f), dp(12f), fillPaint)
        val latin = state.lang == "en"
        p.alts.forEachIndexed { i, a ->
            val col = i % p.cols; val row = i / p.cols
            val l = g.left + col * p.cellW; val t = g.top + row * p.cellH
            tmpRect.set(l + dp(3f), t + dp(3f), l + p.cellW - dp(3f), t + p.cellH - dp(3f))
            val sel = i == p.selected
            if (sel) { fillPaint.color = palette.popupSelected; c.drawRoundRect(tmpRect, dp(9f), dp(9f), fillPaint) }
            setText(letterSize(!isLatin(a), k.h) * 1.1f, if (sel) palette.onAccent else palette.label, face = typeface)
            val label = altLabel(a)
            c.drawText(label, tmpRect.centerX(), baselineFor(text, tmpRect.centerY(), latin && isLatin(a)), text)
        }
    }

    // ---- touch ----
    private fun keyAt(x: Float, y: Float): Key? {
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        val gapX = width * 7f / 1079f
        val gapY = rowGap / 2f
        for (row in rows) for (k in row.keys) {
            if (x >= k.x - gapX && x <= k.x + k.w + gapX && y >= k.y - gapY && y <= k.y + k.h + gapY) {
                val dx = x - (k.x + k.w / 2f); val dy = y - (k.y + k.h / 2f)
                val dist = dx * dx + dy * dy
                if (dist < bestD) { bestD = dist; best = k }
            }
        }
        return best
    }

    private fun toolAt(x: Float, y: Float): Tool? {
        if (y > toolbarH) return null
        val xs = toolCenters()
        var bi = 0
        for (i in xs.indices) if (kotlin.math.abs(x - xs[i]) < kotlin.math.abs(x - xs[bi])) bi = i
        return Tool.values()[bi]
    }

    private fun feedback(k: Key?) {
        if (prefs.haptic) {
            @Suppress("DEPRECATION")
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
        if (prefs.sound) {
            val fx = when (k?.action) {
                Action.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                Action.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                Action.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, -1f)
        }
    }

    private fun hasLongPress(k: Key) = k.alts.isNotEmpty() ||
        k.action == Action.SPACE || k.action == Action.LANG || k.action == Action.COMMA ||
        (k.action == Action.ENTER && state.enter == EnterKind.IMAGE)

    private fun startPress(p: Ptr) {
        val k = p.key ?: return
        feedback(k)
        if (k.action == Action.BACKSPACE) {
            listener?.onKey(Action.BACKSPACE, "")
            p.consumed = true
            handler.postDelayed(repeatRunnable(p.id), REPEAT_DELAY)
            p.repeating = true
            return
        }
        if (hasLongPress(k)) handler.postDelayed({ onLongPress(p.id) }, LONG_PRESS_MS)
    }

    /**
     * Hold-to-delete: after a short delay it repeats, speeding up (55 ms -> 32 ms per character); after about
     * a second and a half of holding it removes a whole word per step. Stops the moment the finger lifts.
     */
    private fun repeatRunnable(id: Int): Runnable = object : Runnable {
        var n = 0
        override fun run() {
            val p = pointers.get(id) ?: return
            if (!p.repeating) return
            if (n >= WORD_AFTER) listener?.onLongAction(Action.BACKSPACE) else listener?.onKey(Action.BACKSPACE, "hold")
            if (prefs.haptic && n % 3 == 0) feedbackLight()
            n++
            handler.postDelayed(this, if (n >= WORD_AFTER) 90L else if (n < 6) 45L else 26L)
        }
    }

    private fun feedbackLight() {
        @Suppress("DEPRECATION")
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    private fun onLongPress(id: Int) {
        val p = pointers.get(id) ?: return
        val k = p.key ?: return
        if (p.longPressed || p.consumed) return
        when (k.action) {
            Action.SPACE, Action.LANG, Action.COMMA, Action.ENTER -> {
                p.longPressed = true; p.consumed = true
                feedbackLight(); listener?.onLongAction(k.action); invalidate(); return
            }
            else -> {}
        }
        if (k.alts.isEmpty()) return
        p.alts = k.alts
        p.longPressed = true
        p.selected = 0
        prepareAltGrid(p, k)
        feedbackLight()
        invalidate()
    }

    private fun updateSelection(p: Ptr, x: Float, y: Float) {
        val g = p.grid
        // clamp the finger into the grid horizontally and use the nearest row
        val col = ((x - g.left) / p.cellW).toInt().coerceIn(0, p.cols - 1)
        val rowsN = ceil(p.alts.size / p.cols.toFloat()).toInt()
        val row = ((y - g.top) / p.cellH).toInt().coerceIn(0, rowsN - 1)
        val idx = row * p.cols + col
        p.selected = if (idx < p.alts.size) idx else p.alts.size - 1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean = try { handleTouch(e) } catch (t: Throwable) {
        CrashReporter.record(context, "KeyboardView.onTouchEvent", t); cancelAll(); true
    }

    private fun handleTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i); val y = e.getY(i); val id = e.getPointerId(i)
                if (y < toolbarH && (status != null || suggestions.isNotEmpty())) {
                    val (l, r) = stripBounds()
                    val idx = if (status != null) -2 else (((x - l) / ((r - l) / 3f)).toInt().coerceIn(0, 2))
                    if (idx == -2 || suggestions.getOrNull(idx) != null) {
                        pressedStrip = idx
                        pointers.put(id, Ptr(id, null, null, x, y).also { it.strip = idx }); invalidate()
                    }
                    return true
                }
                val tool = toolAt(x, y)
                if (tool != null) {
                    pressedTool = tool
                    pointers.put(id, Ptr(id, null, tool, x, y)); invalidate(); return true
                }
                val k = keyAt(x, y)
                val p = Ptr(id, k, null, x, y)
                pointers.put(id, p)
                if (k != null) startPress(p)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = pointers.get(e.getPointerId(i)) ?: continue
                    val x = e.getX(i); val y = e.getY(i)
                    if (p.tool != null || p.strip != -1) continue
                    if (p.longPressed && p.alts.isNotEmpty()) { updateSelection(p, x, y); invalidate(); continue }
                    val nk = keyAt(x, y)
                    if (nk !== p.key && !p.consumed && !p.longPressed) {
                        // finger slid onto another key: re-target (cancel old long-press timers)
                        handler.removeCallbacksAndMessages(null)
                        p.key = nk
                        if (nk != null) startPress(p)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val p = pointers.get(id)
                if (p != null) {
                    pointers.remove(id)
                    p.repeating = false
                    if (p.strip != -1) {
                        pressedStrip = -1
                        if (e.getY(i) < toolbarH) { if (p.strip == -2) listener?.onStatusTap() else listener?.onSuggestion(p.strip) }
                    } else if (p.tool != null) {
                        pressedTool = null
                        if (toolAt(e.getX(i), e.getY(i)) == p.tool) listener?.onTool(p.tool)
                    } else {
                        val k = p.key
                        if (p.longPressed && p.alts.isNotEmpty()) {
                            val a = p.alts.getOrNull(p.selected)
                            if (a != null) listener?.onKey(Action.TEXT, a)
                        } else if (k != null && !p.consumed) {
                            val out = when (k.action) {
                                Action.TEXT, Action.COMMA, Action.PERIOD -> k.label
                                else -> ""
                            }
                            listener?.onKey(k.action, out)
                        }
                    }
                }
                if (pointers.size() == 0) handler.removeCallbacksAndMessages(null)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
        pointers.clear()
        pressedTool = null
        pressedStrip = -1
        invalidate()
    }

    override fun onDetachedFromWindow() { cancelAll(); super.onDetachedFromWindow() }

    companion object {
        const val TOOLBAR_DP = 60f
        /** Measured on the reference screenshot (key height 121 px): alef is 49 px tall, Arabic baseline at 88 px. */
        const val ALEF_H = 49f / 121f
        const val BASE_ARABIC = 88f / 121f
        const val CAP_H = 0.36f
        const val BASE_LATIN = 0.665f
        const val KEY_DP = 46.1f
        const val ROW_GAP_DP = 11.5f
        const val BOTTOM_PAD_DP = 14f
        const val LONG_PRESS_MS = 380L
        const val REPEAT_DELAY = 380L
        const val WORD_AFTER = 26
    }
}
