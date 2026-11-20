package app.mykeyboard.text

data class Correction(val original: String, val replacement: String)

/** Suggestions and conservative auto-correction on top of [WordDict]. Pure Kotlin, no Android types. */
class SuggestionEngine(private val dicts: Map<String, WordDict>, private val learned: LearnedWords) {

    fun isWordChar(lang: String, c: Char) =
        if (lang == "ar") c in 'ء'..'ي' || c in 'ً'..'ْ'
        else c in 'a'..'z' || c in 'A'..'Z' || c == '\'' || c == '’'

    /** The word being typed: the trailing run of letters before the cursor. */
    fun currentWord(before: CharSequence, lang: String): String {
        var i = before.length
        while (i > 0 && isWordChar(lang, before[i - 1])) i--
        var w = before.substring(i)
        w = w.trimStart('\'', '’')         // a leading quote is punctuation, not part of the word
        return w
    }

    private fun matchCase(typed: String, w: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> w.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> w.replaceFirstChar { it.uppercase() }
        else -> w
    }

    private fun isValid(lang: String, w: String): Boolean {
        val d = dicts[lang]
        return d == null || !d.ready || d.contains(w) || learned.isKnown(w) || w.length < 2
    }

    /** Up to three strip entries for what is currently being typed ([] = show the tool icons). */
    fun suggest(before: CharSequence, lang: String): List<String> {
        val d = dicts[lang]?.takeIf { it.ready } ?: return emptyList()
        val w = currentWord(before, lang)
        if (w.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        val valid = isValid(lang, w)
        val comps = (learned.words(w) + d.completions(w, 6)).map { matchCase(w, it) }
        if (valid) {
            out.add(w); out.addAll(comps)
        } else {
            val corr = d.corrections(w, 3).map { matchCase(w, it) }
            corr.firstOrNull()?.let { out.add(it) }
            out.add(w)
            out.addAll(comps.take(1))                       // what you are probably typing (learned words come first)
            out.addAll(corr.drop(1)); out.addAll(comps)
        }
        return out.take(3)
    }

    /** Called when a word boundary (space, punctuation, enter) is typed. Returns the fix to apply, if any. */
    fun autoCorrect(before: CharSequence, lang: String): Correction? {
        val d = dicts[lang]?.takeIf { it.ready } ?: return null
        val w = currentWord(before, lang)
        if (w.length < 3 || isValid(lang, w)) return null
        // lone "i" / "i'm" style capitalisation is handled separately below
        val tail = before.length - w.length
        val rest = before.substring(0, tail).trimEnd(' ', '\'', '’')
        val atSentenceStart = rest.isEmpty() || rest.last() in ".!?؟\n"
        if (w.substring(1).any { it.isUpperCase() }) return null                   // CamelCase / ALLCAPS: acronyms, names
        if (w[0].isUpperCase() && !atSentenceStart) return null                    // probably a name
        val best = d.corrections(w, 1).firstOrNull() ?: return null
        val r = d.rank(best)
        if (r < 0 || r > 25000) return null
        return Correction(w, matchCase(w, best))
    }

    /** English: "i" -> "I", "i'm" -> "I'm". */
    fun capitalizeI(before: CharSequence, lang: String): Correction? {
        if (lang != "en") return null
        val w = currentWord(before, lang)
        if (w == "i" || (w.startsWith("i'") && w.length <= 5)) return Correction(w, "I" + w.substring(1))
        return null
    }

    /** The user typed [word] and moved on without a fix: remember it so it stops being corrected. */
    fun learn(word: String, lang: String) {
        if (word.length >= 2 && !(dicts[lang]?.contains(word) ?: false)) learned.bump(word)
    }

    fun rejectCorrection(original: String) = learned.bump(original, 2)
}
