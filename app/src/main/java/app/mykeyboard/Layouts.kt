package app.mykeyboard

enum class Action { TEXT, BACKSPACE, SHIFT, ENTER, SPACE, SYMBOLS, SYMBOLS2, LETTERS, LANG, COMMA, PERIOD }
enum class Style { NORMAL, FUNC, ACCENT }
enum class Page { LETTERS, SYM1, SYM2 }
enum class Shift { OFF, ON, LOCK }
enum class EnterKind { ENTER, GO, SEARCH, SEND, NEXT, DONE, IMAGE }

class Key(
    val action: Action = Action.TEXT,
    /** Text drawn on the key and, for TEXT keys, the text that is committed. */
    val label: String = "",
    /** Small text in the top-right corner (digits on the top row). */
    val hint: String? = null,
    /** Long-press alternatives. */
    val alts: List<String> = emptyList(),
    val weight: Float = 1f,
    val style: Style = Style.NORMAL,
    /** Draw [label] strictly left-to-right (used for the "؟٣٢١" pill). */
    val ltrLabel: Boolean = false,
    /** Two-line key (emoji / period): small symbol on top, [label] below. */
    val top: String? = null,
) {
    // runtime geometry, filled by KeyboardView
    var x = 0f; var y = 0f; var w = 0f; var h = 0f
    fun contains(px: Float, py: Float) = px >= x && px < x + w && py >= y && py < y + h
    val isLetterLike get() = action == Action.TEXT && label.isNotEmpty()
}

class Row(val keys: List<Key>, val padStart: Float = 0f, val padEnd: Float = 0f)

data class LayoutState(
    val lang: String,          // "ar" | "en"
    val page: Page = Page.LETTERS,
    val shift: Shift = Shift.OFF,
    val arabicDigits: Boolean = true,
    val enter: EnterKind = EnterKind.ENTER,
)

object Layouts {
    const val DOTTED = "◌"
    private val AR_DIGITS = "١٢٣٤٥٦٧٨٩٠"
    private val EN_DIGITS = "1234567890"

    private val AR_ALTS = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ء", "ٱ"),
        "ي" to listOf("ئ", "ى"),
        "ى" to listOf("ي", "ئ"),
        "و" to listOf("ؤ"),
        "ؤ" to listOf("و", "ء"),
        "ء" to listOf("ئ", "ؤ", "أ", "إ"),
        "ه" to listOf("ة"),
        "ة" to listOf("ه"),
        "ك" to listOf("گ", "ک"),
        "ب" to listOf("پ"),
        "ف" to listOf("ڤ"),
        "ج" to listOf("چ"),
        "ز" to listOf("ژ"),
        "ل" to listOf("لا", "لأ", "لإ", "لآ"),
    )

    private val EN_ALTS = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        "c" to listOf("ç", "ć", "č"),
        "d" to listOf("ď", "đ"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "į"),
        "l" to listOf("ł"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ò", "ó", "ô", "ö", "œ", "ø", "õ", "ō"),
        "s" to listOf("ß", "ś", "š"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
    )

    /** Arabic marks (tashkeel) are shown on a dotted circle but committed alone. */
    val TASHKEEL = listOf("َ", "ً", "ُ", "ٌ", "ِ", "ٍ", "ّ", "ْ", "،", "؛", "؟", "!")
    private val EN_PERIOD_ALTS = listOf("!", "?", ":", ";", "'", "\"", "-", "_", "(", ")", "@", "/")

    fun isTashkeel(s: String) = s.length == 1 && s[0] in 'ً'..'ْ'

    private fun letter(c: String, hint: String? = null, alts: List<String>) =
        Key(Action.TEXT, c, hint, listOfNotNull(hint) + alts)

    fun rows(s: LayoutState): List<Row> = when (s.page) {
        Page.LETTERS -> if (s.lang == "ar") arabic(s) else english(s)
        Page.SYM1 -> symbols1(s)
        Page.SYM2 -> symbols2(s)
    }

    private fun digitsFor(s: LayoutState) = if (s.lang == "ar" && s.arabicDigits) AR_DIGITS else EN_DIGITS

    private fun arabic(s: LayoutState): List<Row> {
        val d = digitsFor(s)
        fun alts(c: String, western: String?) =
            (AR_ALTS[c] ?: emptyList()) + listOfNotNull(western)
        val r1 = "ضصثقفغعهخحج".mapIndexed { i, ch ->
            val c = ch.toString()
            val hint = if (i < 10) d[i].toString() else null
            Key(Action.TEXT, c, hint, listOfNotNull(hint) + alts(c, if (i < 10) EN_DIGITS[i].toString().takeIf { hint != it } else null))
        }
        val r2 = "شسيبلاتنمكط".map { Key(Action.TEXT, it.toString(), null, AR_ALTS[it.toString()] ?: emptyList()) }
        val r3 = "ذءؤرىةوزظد".map { Key(Action.TEXT, it.toString(), null, AR_ALTS[it.toString()] ?: emptyList()) } +
            Key(Action.BACKSPACE, style = Style.FUNC)
        return listOf(Row(r1), Row(r2), Row(r3), bottom(s))
    }

    private fun english(s: LayoutState): List<Row> {
        val up = s.shift != Shift.OFF
        fun k(ch: Char, hint: String?): Key {
            val base = ch.toString()
            val label = if (up) base.uppercase() else base
            val alts = (EN_ALTS[base] ?: emptyList()).map { if (up) it.uppercase() else it }
            return Key(Action.TEXT, label, hint, listOfNotNull(hint) + alts)
        }
        val r1 = "qwertyuiop".mapIndexed { i, c -> k(c, EN_DIGITS[i].toString()) }
        val r2 = "asdfghjkl".map { k(it, null) }
        val r3 = listOf(Key(Action.SHIFT, weight = 1.5f, style = Style.FUNC)) +
            "zxcvbnm".map { k(it, null) } +
            Key(Action.BACKSPACE, weight = 1.5f, style = Style.FUNC)
        return listOf(Row(r1), Row(r2, 0.5f, 0.5f), Row(r3), bottom(s))
    }

    private fun sym(c: String, alts: List<String> = emptyList()) = Key(Action.TEXT, c, null, alts)

    private fun symbols1(s: LayoutState): List<Row> {
        val d = digitsFor(s)
        val west = EN_DIGITS
        val r1 = d.mapIndexed { i, c ->
            Key(Action.TEXT, c.toString(), null, if (d != west) listOf(west[i].toString()) else emptyList())
        }
        val r2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { sym(it) }
        val third = if (s.lang == "ar") listOf("*", "\"", "'", ":", "؛", "!", "؟") else listOf("*", "\"", "'", ":", ";", "!", "?")
        val r3 = listOf(Key(Action.SYMBOLS2, "=\\<", weight = 1.5f, style = Style.FUNC)) +
            third.map { sym(it) } + Key(Action.BACKSPACE, weight = 1.5f, style = Style.FUNC)
        return listOf(Row(r1), Row(r2), Row(r3), bottom(s))
    }

    private fun symbols2(s: LayoutState): List<Row> {
        val r1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { sym(it) }
        val r2 = listOf("£", "€", "¥", "¢", "°", "©", "®", "{", "}", "\\").map { sym(it) }
        val r3 = listOf(Key(Action.SYMBOLS, "?123", weight = 1.5f, style = Style.FUNC)) +
            listOf("%", "[", "]", "<", ">", "«", "»").map { sym(it) } +
            Key(Action.BACKSPACE, weight = 1.5f, style = Style.FUNC)
        return listOf(Row(r1), Row(r2), Row(r3), bottom(s))
    }

    /** Bottom row, proportions measured from the reference screenshot (146 / 92 / 92 / 414 / 92 / 146 px). */
    private fun bottom(s: LayoutState): Row {
        val ar = s.lang == "ar"
        val toggle = when {
            s.page != Page.LETTERS -> if (ar) Key(Action.LETTERS, "أبجد", weight = 1.76f, style = Style.ACCENT)
                                      else Key(Action.LETTERS, "ABC", weight = 1.76f, style = Style.ACCENT)
            ar -> Key(Action.SYMBOLS, "؟٣٢١", weight = 1.76f, style = Style.ACCENT, ltrLabel = true)
            else -> Key(Action.SYMBOLS, "?123", weight = 1.76f, style = Style.ACCENT)
        }
        val comma = if (ar) "،" else ","
        val period = if (ar && s.page == Page.LETTERS) {
            Key(Action.PERIOD, ".", null, listOf(DOTTED + TASHKEEL[0]).let { TASHKEEL }, 1.1f, Style.FUNC, top = DOTTED + "ً")
        } else {
            Key(Action.PERIOD, ".", null, EN_PERIOD_ALTS, 1.1f, Style.FUNC)
        }
        return Row(
            listOf(
                toggle,
                Key(Action.COMMA, comma, null, emptyList(), 1.1f, Style.FUNC, top = "☺"),
                Key(Action.LANG, "", weight = 1.1f),
                Key(Action.SPACE, if (ar) "العربية" else "English", weight = 5f),
                period,
                Key(Action.ENTER, "", weight = 1.76f, style = Style.ACCENT),
            )
        )
    }
}
