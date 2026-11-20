package app.mykeyboard

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

interface PanelHost {
    fun closePanel()
    fun insert(text: String)
    fun backspace()
    fun openFontImport()
    fun openSettings()
    fun openPanel(tool: Tool)
    fun cycleTheme()
    fun showImePicker()
    fun fontChanged()
    fun toast(resId: Int)
}

abstract class Panel(context: Context, protected val palette: Palette) : LinearLayout(context) {
    protected val ui = Ui
    protected val isRtl get() = layoutDirection == View.LAYOUT_DIRECTION_RTL
    init { orientation = VERTICAL; setBackgroundColor(palette.background) }

    protected fun header(title: String, host: PanelHost, vararg actions: View): LinearLayout {
        val c = context
        val row = LinearLayout(c).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dpi(c, 8f), ui.dpi(c, 2f), ui.dpi(c, 8f), ui.dpi(c, 2f))
        }
        val back = IconView(c, Icon.KEYBOARD, palette.funcLabel).apply {
            background = ui.round(palette.funcKey, ui.dp(c, 18f))
            contentDescription = c.getString(R.string.back_to_keyboard)
        }
        ui.pressable(back, { host.closePanel() })
        row.addView(back, ui.lp(ui.dpi(c, 52f), ui.dpi(c, 36f)))
        row.addView(ui.text(c, title, 16f, palette.label, Gravity.CENTER_VERTICAL or Gravity.START).apply {
            setPadding(ui.dpi(c, 12f), 0, ui.dpi(c, 12f), 0); setTypeface(typeface, Typeface.BOLD)
        }, ui.lp(0, ui.WRAP, 1f))
        actions.forEach { row.addView(it) }
        return row
    }
}

// ------------------------------------------------------------------------------------------------ fonts
class FontPanel(
    context: Context, palette: Palette,
    private val prefs: Prefs, private val fonts: FontRepository, private val host: PanelHost,
) : Panel(context, palette) {
    private val arabicUi = context.resources.configuration.locales[0].language == "ar"
    private var pendingDelete: String? = null
    private lateinit var filters: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var list: LinearLayout

    init { build() }

    fun refresh() { removeAllViews(); build() }

    private fun build() {
        val c = context
        val add = ui.pill(c, "+  " + c.getString(R.string.font_add), palette.accent, palette.onAccent, 13f)
        ui.pressable(add, { host.openFontImport() })
        addView(header(c.getString(R.string.fonts_title), host, add), ui.lp(ui.MATCH, ui.WRAP))

        val fs = HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false }
        filters = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ui.dpi(c, 10f), 0, ui.dpi(c, 10f), 0) }
        fs.addView(filters)
        addView(fs, ui.lp(ui.MATCH, ui.dpi(c, 38f)))

        chipsScroll = HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false }
        chips = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ui.dpi(c, 10f), 0, ui.dpi(c, 10f), 0) }
        chipsScroll.addView(chips)
        addView(chipsScroll, ui.lp(ui.MATCH, ui.dpi(c, 36f)))

        val sv = ScrollView(c).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dpi(c, 8f), 0, ui.dpi(c, 8f), ui.dpi(c, 8f)) }
        sv.addView(list)
        addView(sv, ui.lp(ui.MATCH, 0, 1f))
        render()
    }

    private fun selected() = fonts.find(prefs.fontId) ?: fonts.builtInFonts().first { it.id == FontRepository.DEFAULT_ID }

    private fun groupLabel(g: String) = context.getString(when (g) {
        "modern" -> R.string.cat_modern
        "kufi" -> R.string.cat_kufi
        "naskh" -> R.string.cat_naskh
        "calligraphy" -> R.string.cat_calligraphy
        "display" -> R.string.cat_display
        "fun" -> R.string.cat_fun
        else -> R.string.cat_latin
    })

    private fun render() {
        val c = context
        val sel = selected()
        val user = fonts.userFonts()
        val builtIn = fonts.builtInFonts()
        val favs = prefs.favoriteFonts
        val hasFavs = builtIn.any { it.id in favs } || user.any { it.id in favs }

        // validate the stored filter (a category may be gone, e.g. the last user font was deleted)
        val groups = GROUP_ORDER.filter { g -> builtIn.any { it.group == g } }
        var filter = prefs.fontFilter
        val valid = filter == "all" || (filter == "fav" && hasFavs) || (filter == "mine" && user.isNotEmpty()) || filter in groups
        if (!valid) { filter = "all"; prefs.fontFilter = "all" }

        // ---- category chips
        filters.removeAllViews()
        val entries = ArrayList<Pair<String, String>>()
        entries.add("all" to c.getString(R.string.cat_all))
        if (hasFavs) entries.add("fav" to "★ " + c.getString(R.string.cat_favorites))
        if (user.isNotEmpty()) entries.add("mine" to c.getString(R.string.fonts_mine))
        groups.forEach { entries.add(it to groupLabel(it)) }
        for ((key, label) in entries) {
            val on = key == filter
            val chip = ui.pill(c, label, if (on) palette.popupSelected else palette.key, if (on) palette.onAccent else palette.label, 13f)
            ui.pressable(chip, { prefs.fontFilter = key; render() })
            filters.addView(chip, ui.lp(ui.WRAP, ui.dpi(c, 32f)).apply { marginEnd = ui.dpi(c, 6f); topMargin = ui.dpi(c, 3f) })
        }

        // ---- weight chips (only for fonts that have weights)
        chips.removeAllViews()
        chipsScroll.visibility = if (sel.variable) View.VISIBLE else View.GONE
        if (sel.variable) {
            val weights = listOf(300 to R.string.weight_light, 400 to R.string.weight_regular, 500 to R.string.weight_medium,
                600 to R.string.weight_semibold, 700 to R.string.weight_bold, 900 to R.string.weight_black)
            for ((w, res) in weights) {
                val on = prefs.fontWeight.coerceIn(sel.minWeight, sel.maxWeight) == w.coerceIn(sel.minWeight, sel.maxWeight)
                val chip = ui.pill(c, c.getString(res), if (on) palette.accent else palette.funcKey, if (on) palette.onAccent else palette.funcLabel, 12f)
                ui.pressable(chip, { prefs.fontWeight = w; host.fontChanged(); render() })
                chips.addView(chip, ui.lp(ui.WRAP, ui.dpi(c, 30f)).apply { marginEnd = ui.dpi(c, 6f); topMargin = ui.dpi(c, 3f) })
            }
        }

        // ---- list
        list.removeAllViews()
        val all = user + builtIn
        when (filter) {
            "all" -> {
                if (hasFavs) { section(c.getString(R.string.cat_favorites)); grid(all.filter { it.id in favs }) }
                if (user.isNotEmpty()) { section(c.getString(R.string.fonts_mine)); grid(user) }
                groups.forEach { g -> section(groupLabel(g)); grid(builtIn.filter { it.group == g }) }
            }
            "fav" -> grid(all.filter { it.id in favs })
            "mine" -> grid(user)
            else -> grid(builtIn.filter { it.group == filter })
        }
    }

    private fun section(title: String) {
        val c = context
        list.addView(ui.text(c, title, 12f, palette.hint, Gravity.CENTER_VERTICAL or Gravity.START).apply {
            setPadding(ui.dpi(c, 6f), ui.dpi(c, 10f), ui.dpi(c, 6f), ui.dpi(c, 4f))
        }, ui.lp(ui.MATCH, ui.WRAP))
    }

    private fun grid(items: List<FontEntry>) {
        val c = context
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { row.addView(card(it), ui.lp(0, ui.dpi(c, 62f), 1f).apply { setMargins(ui.dpi(c, 3f), ui.dpi(c, 3f), ui.dpi(c, 3f), ui.dpi(c, 3f)) }) }
            if (pair.size == 1) row.addView(View(c), ui.lp(0, 1, 1f))
            list.addView(row, ui.lp(ui.MATCH, ui.WRAP))
        }
    }

    private fun card(f: FontEntry): View {
        val c = context
        val isSel = f.id == prefs.fontId
        val isFav = f.id in prefs.favoriteFonts
        val tf = fonts.typeface(f, if (f.variable) prefs.fontWeight else 400)
        val box = FrameLayout(c).apply {
            background = ui.round(palette.key, ui.dp(c, 14f), if (isSel) palette.accent else 0, if (isSel) ui.dp(c, 2f) else 0f)
        }
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dpi(c, 12f), ui.dpi(c, 4f), ui.dpi(c, 12f), ui.dpi(c, 4f))
        }
        col.addView(ui.text(c, f.displayName(arabicUi), 17f, palette.label, Gravity.START).apply {
            typeface = tf; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; includeFontPadding = true
            if (isSel) setPadding(0, 0, ui.dpi(c, 22f), 0)
        })
        col.addView(ui.text(c, "أبجد  Abc  ١٢٣", 13f, palette.hint, Gravity.START).apply {
            typeface = tf; maxLines = 1; includeFontPadding = true; setPadding(0, 0, ui.dpi(c, 30f), 0)
        })
        box.addView(col, FrameLayout.LayoutParams(ui.MATCH, ui.MATCH))
        if (isSel) {
            box.addView(IconView(c, Icon.CHECK, palette.accent), FrameLayout.LayoutParams(ui.dpi(c, 28f), ui.dpi(c, 28f), Gravity.TOP or Gravity.END))
        }
        val star = IconView(c, if (isFav) Icon.STAR_FILLED else Icon.STAR, if (isFav) palette.accent else palette.hint)
        ui.pressable(star, {
            val cur = prefs.favoriteFonts
            prefs.favoriteFonts = if (f.id in cur) cur - f.id else cur + f.id
            render()
        })
        box.addView(star, FrameLayout.LayoutParams(ui.dpi(c, 32f), ui.dpi(c, 32f), Gravity.BOTTOM or Gravity.END))
        if (!f.builtIn) {
            val confirm = pendingDelete == f.id
            val del = IconView(c, Icon.TRASH, if (confirm) 0xFFFF6B6B.toInt() else palette.hint)
            ui.pressable(del, {
                if (pendingDelete == f.id) {
                    if (prefs.fontId == f.id) { prefs.fontId = FontRepository.DEFAULT_ID; host.fontChanged() }
                    prefs.favoriteFonts = prefs.favoriteFonts - f.id
                    fonts.deleteUserFont(f.id); pendingDelete = null; render()
                } else { pendingDelete = f.id; render(); host.toast(R.string.font_delete_confirm) }
            })
            box.addView(del, FrameLayout.LayoutParams(ui.dpi(c, 30f), ui.dpi(c, 30f), Gravity.BOTTOM or Gravity.END).apply { marginEnd = ui.dpi(c, 30f) })
        }
        ui.pressable(box, {
            pendingDelete = null
            prefs.fontId = f.id
            host.fontChanged(); render()
        })
        return box
    }

    companion object {
        val GROUP_ORDER = listOf("modern", "kufi", "naskh", "calligraphy", "display", "fun", "latin")
    }
}

// ------------------------------------------------------------------------------------------------ emoji
class EmojiPanel(
    context: Context, palette: Palette, private val prefs: Prefs, private val host: PanelHost,
) : Panel(context, palette) {
    private val paint = Paint()
    private val supported = HashMap<String, Boolean>()
    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
    private val tabs = LinearLayout(context)
    private var current = 1
    private val cols = 8

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        val c = context
        addView(scroll, ui.lp(ui.MATCH, 0, 1f))
        tabs.orientation = HORIZONTAL; tabs.gravity = Gravity.CENTER_VERTICAL
        val bar = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(ui.dpi(c, 8f), ui.dpi(c, 4f), ui.dpi(c, 8f), ui.dpi(c, 8f)) }
        val abc = ui.pill(c, c.getString(R.string.abc_label), palette.funcKey, palette.funcLabel, 14f)
        ui.pressable(abc, { host.closePanel() })
        bar.addView(abc, ui.lp(ui.dpi(c, 56f), ui.dpi(c, 38f)))
        val hs = HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false }
        hs.addView(tabs)
        bar.addView(hs, ui.lp(0, ui.dpi(c, 38f), 1f))
        val del = IconView(c, Icon.BACKSPACE, palette.funcLabel).apply { background = ui.round(palette.funcKey, ui.dp(c, 12f)) }
        ui.pressable(del, { host.backspace() })
        bar.addView(del, ui.lp(ui.dpi(c, 52f), ui.dpi(c, 38f)))
        addView(bar, ui.lp(ui.MATCH, ui.WRAP))
        buildTabs()
        show(if (prefs.recentEmoji.isEmpty()) 1 else 0)
    }

    private fun ok(e: String) = supported.getOrPut(e) { paint.hasGlyph(e) }

    private fun buildTabs() {
        val c = context
        tabs.removeAllViews()
        val icons = listOf("🕘") + EmojiData.categories.map { it.icon }
        icons.forEachIndexed { i, ic ->
            val t = ui.text(c, ic, 19f, palette.label).apply {
                background = if (i == current) ui.round(palette.funcKey, ui.dp(c, 12f)) else null
            }
            ui.pressable(t, { show(i) })
            tabs.addView(t, ui.lp(ui.dpi(c, 42f), ui.dpi(c, 38f)))
        }
    }

    private fun show(index: Int) {
        current = index
        buildTabs()
        val c = context
        val list = if (index == 0) prefs.recentEmoji else EmojiData.categories[index - 1].emojis.filter { ok(it) }
        val grid = LinearLayout(c).apply { orientation = VERTICAL; setPadding(ui.dpi(c, 6f), ui.dpi(c, 4f), ui.dpi(c, 6f), ui.dpi(c, 4f)) }
        if (list.isEmpty()) {
            grid.addView(ui.text(c, c.getString(R.string.emoji_recent_empty), 14f, palette.hint).apply { setPadding(0, ui.dpi(c, 40f), 0, 0) }, ui.lp(ui.MATCH, ui.WRAP))
        }
        list.chunked(cols).forEach { chunk ->
            val row = LinearLayout(c).apply { orientation = HORIZONTAL }
            for (e in chunk) {
                val t = ui.text(c, e, 26f, palette.label).apply { includeFontPadding = true }
                ui.pressable(t, { pick(e) })
                row.addView(t, ui.lp(0, ui.dpi(c, 44f), 1f))
            }
            repeat(cols - chunk.size) { row.addView(View(c), ui.lp(0, 1, 1f)) }
            grid.addView(row, ui.lp(ui.MATCH, ui.WRAP))
        }
        scroll.removeAllViews(); scroll.addView(grid); scroll.scrollTo(0, 0)
    }

    private fun pick(e: String) {
        host.insert(e)
        val r = (listOf(e) + prefs.recentEmoji.filter { it != e }).take(32)
        prefs.recentEmoji = r
    }
}

// ------------------------------------------------------------------------------------------------ clipboard
class ClipboardPanel(
    context: Context, palette: Palette, private val prefs: Prefs, private val store: ClipboardStore, private val host: PanelHost,
) : Panel(context, palette) {
    private val body = LinearLayout(context).apply { orientation = VERTICAL }

    init { build() }

    private fun build() {
        removeAllViews()
        val c = context
        val clear = ui.pill(c, c.getString(R.string.clipboard_clear), palette.key, palette.label, 13f)
        ui.pressable(clear, { store.clearUnpinned(); build() })
        addView(header(c.getString(R.string.clipboard_title), host, clear), ui.lp(ui.MATCH, ui.WRAP))
        val sv = ScrollView(c).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        body.removeAllViews()
        body.setPadding(ui.dpi(c, 10f), ui.dpi(c, 4f), ui.dpi(c, 10f), ui.dpi(c, 8f))
        sv.addView(body)
        addView(sv, ui.lp(ui.MATCH, 0, 1f))

        if (!prefs.clipboardHistory) {
            body.addView(ui.text(c, c.getString(R.string.clipboard_off), 14f, palette.hint).apply { setPadding(0, ui.dpi(c, 24f), 0, ui.dpi(c, 12f)) })
            val on = ui.pill(c, c.getString(R.string.clipboard_enable), palette.accent, palette.onAccent)
            ui.pressable(on, { prefs.clipboardHistory = true; build() })
            body.addView(on, ui.lp(ui.WRAP, ui.WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
            return
        }
        val items = store.items()
        if (items.isEmpty()) {
            body.addView(ui.text(c, c.getString(R.string.clipboard_empty), 14f, palette.hint).apply { setPadding(ui.dpi(c, 12f), ui.dpi(c, 40f), ui.dpi(c, 12f), 0) })
            return
        }
        items.forEach { item ->
            val row = LinearLayout(c).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = ui.round(palette.key, ui.dp(c, 14f))
                setPadding(ui.dpi(c, 14f), ui.dpi(c, 8f), ui.dpi(c, 4f), ui.dpi(c, 8f))
            }
            row.addView(ui.text(c, item.text, 15f, palette.label, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }, ui.lp(0, ui.WRAP, 1f))
            val pin = IconView(c, Icon.PIN, if (item.pinned) palette.accent else palette.hint)
            ui.pressable(pin, { store.togglePin(item.text); build() })
            row.addView(pin, ui.lp(ui.dpi(c, 40f), ui.dpi(c, 40f)))
            val del = IconView(c, Icon.CLOSE, palette.hint)
            ui.pressable(del, { store.remove(item.text); build() })
            row.addView(del, ui.lp(ui.dpi(c, 40f), ui.dpi(c, 40f)))
            ui.pressable(row, { host.insert(item.text); host.closePanel() })
            body.addView(row, ui.lp(ui.MATCH, ui.WRAP).apply { bottomMargin = ui.dpi(c, 6f) })
        }
    }
}

// ------------------------------------------------------------------------------------------------ menu
class MenuPanel(context: Context, palette: Palette, private val prefs: Prefs, private val host: PanelHost) : Panel(context, palette) {
    init {
        val c = context
        addView(header(c.getString(R.string.menu_title), host), ui.lp(ui.MATCH, ui.WRAP))
        app.mykeyboard.update.UpdateInfo.fromJson(prefs.availableUpdate)?.let { u ->
            val banner = ui.pill(c, c.getString(R.string.menu_update, u.version), palette.accent, palette.onAccent, 13f)
            ui.pressable(banner, { host.openSettings() })
            addView(banner, ui.lp(ui.WRAP, ui.WRAP).apply { setMargins(ui.dpi(c, 12f), ui.dpi(c, 4f), ui.dpi(c, 12f), 0) })
        }
        val tiles = listOf(
            Triple(Icon.PALETTE, R.string.fonts_title) { host.openPanel(Tool.FONTS) },
            Triple(Icon.SMILEY, R.string.emoji_title) { host.openPanel(Tool.EMOJI) },
            Triple(Icon.CLIPBOARD, R.string.clipboard_title) { host.openPanel(Tool.CLIPBOARD) },
            Triple(Icon.MOON, R.string.menu_theme) { host.cycleTheme() },
            Triple(Icon.GLOBE, R.string.menu_switch_keyboard) { host.showImePicker() },
            Triple(Icon.GEAR, R.string.menu_settings) { host.openSettings() },
        )
        val grid = LinearLayout(c).apply { orientation = VERTICAL; setPadding(ui.dpi(c, 8f), ui.dpi(c, 6f), ui.dpi(c, 8f), ui.dpi(c, 6f)) }
        tiles.chunked(3).forEach { chunk ->
            val row = LinearLayout(c).apply { orientation = HORIZONTAL }
            chunk.forEach { (icon, label, action) ->
                val tile = LinearLayout(c).apply {
                    orientation = VERTICAL; gravity = Gravity.CENTER
                    background = ui.round(palette.key, ui.dp(c, 16f))
                }
                tile.addView(IconView(c, icon, palette.funcLabel), ui.lp(ui.dpi(c, 40f), ui.dpi(c, 40f)))
                tile.addView(ui.text(c, c.getString(label), 13f, palette.label))
                ui.pressable(tile, { action() })
                row.addView(tile, ui.lp(0, ui.dpi(c, 76f), 1f).apply { setMargins(ui.dpi(c, 4f), ui.dpi(c, 3f), ui.dpi(c, 4f), ui.dpi(c, 3f)) })
            }
            grid.addView(row, ui.lp(ui.MATCH, ui.WRAP))
        }
        addView(grid, ui.lp(ui.MATCH, ui.WRAP))
    }
}
