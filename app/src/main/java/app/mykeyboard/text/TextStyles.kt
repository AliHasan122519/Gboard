package app.mykeyboard.text

import org.json.JSONArray

/** Unicode "fancy text": Latin letters/digits are swapped for look-alike styled characters that survive in any app. */
class TextStyle(val id: String, private val table: IntArray?, val decorator: Char? = null, val kashida: Boolean = false) {
    fun apply(text: CharSequence): String {
        val sb = StringBuilder()
        val s = text.toString()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i); val n = Character.charCount(cp)
            val mapped = if (table != null) mapOne(cp) else cp
            sb.appendCodePoint(mapped)
            if (decorator != null && !Character.isWhitespace(cp)) sb.append(decorator)
            if (kashida && isArabic(cp) && joinsNext(cp) && i + n < s.length && isArabic(s.codePointAt(i + n))) sb.append('ـ')
            i += n
        }
        return sb.toString()
    }

    /** Style for a single typed character (the next letter is unknown, so the stretch is added after every joining letter). */
    fun typed(ch: String): String {
        if (!kashida) return apply(ch)
        return if (ch.length == 1 && isArabic(ch[0].code) && joinsNext(ch[0].code)) ch + "\u0640" else ch
    }

    private fun mapOne(cp: Int): Int {
        val t = table ?: return cp
        return when (cp) {
            in 'A'.code..'Z'.code -> t[cp - 'A'.code]
            in 'a'.code..'z'.code -> t[26 + cp - 'a'.code]
            in '0'.code..'9'.code -> t[52 + cp - '0'.code]
            else -> cp
        }
    }

    companion object {
        private fun isArabic(cp: Int) = cp in 0x0621..0x064A
        // letters that do not connect to the following letter
        private val NON_JOINING = "ءاأإآٱدذرزوؤةى".toSet()
        private fun joinsNext(cp: Int) = cp.toChar() !in NON_JOINING

        const val ID_NONE = "none"

        /** [json] = assets/styles.json: [{"id": "...", "map": "<62 chars: A-Z a-z 0-9>"}]. */
        fun load(json: String): List<TextStyle> {
            val arr = JSONArray(json)
            val list = ArrayList<TextStyle>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val cps = o.getString("map").let { m -> val a = IntArray(m.codePointCount(0, m.length)); var k = 0; var j = 0; while (j < m.length) { val cp = m.codePointAt(j); a[k++] = cp; j += Character.charCount(cp) }; a }
                if (cps.size == 62) list.add(TextStyle(o.getString("id"), cps))
            }
            list.add(TextStyle("underline", null, '̲'))
            list.add(TextStyle("strike", null, '̶'))
            list.add(TextStyle("kashida", null, null, kashida = true))
            return list
        }
    }
}
