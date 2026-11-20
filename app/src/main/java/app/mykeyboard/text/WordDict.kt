package app.mykeyboard.text

/** In-memory frequency dictionary for one language (Arabic or English). Words arrive ordered by frequency. */
class WordDict(val lang: String) {
    private val words = ArrayList<String>()
    private val counts = ArrayList<Int>()
    private val normToIdx = HashMap<String, Int>()
    private var keys: Array<String> = emptyArray()
    private var keyIdx: IntArray = IntArray(0)
    val size get() = words.size
    val ready get() = words.isNotEmpty()

    private val alphabet = if (lang == "ar") "ءابتثجحخدذرزسشصضطظعغفقكلمنهوي" else "abcdefghijklmnopqrstuvwxyz'"

    fun norm(w: String): String = normalize(lang, w)

    /** Lines are "word<TAB>count". */
    fun load(lines: Sequence<String>) {
        for (line in lines) {
            val t = line.indexOf('\t'); if (t <= 0) continue
            val w = line.substring(0, t); val c = line.substring(t + 1).trim().toIntOrNull() ?: continue
            val n = norm(w)
            val idx = words.size
            words.add(w); counts.add(c)
            if (!normToIdx.containsKey(n)) normToIdx[n] = idx
        }
        val sorted = normToIdx.entries.sortedBy { it.key }
        keys = Array(sorted.size) { sorted[it].key }
        keyIdx = IntArray(sorted.size) { sorted[it].value }
    }

    fun contains(w: String) = normToIdx.containsKey(norm(w))

    /** Rank (0 = most frequent) of a word, or -1. */
    fun rank(w: String) = normToIdx[norm(w)] ?: -1

    fun display(w: String): String? = normToIdx[norm(w)]?.let { words[it] }

    /** Most frequent words that start with [prefix] (excluding the word itself). */
    fun completions(prefix: String, n: Int): List<String> {
        val p = norm(prefix); if (p.isEmpty() || keys.isEmpty()) return emptyList()
        var lo = 0; var hi = keys.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (keys[mid] < p) lo = mid + 1 else hi = mid }
        val found = ArrayList<Int>()
        var i = lo
        while (i < keys.size && keys[i].startsWith(p) && found.size < 400) { if (keys[i] != p) found.add(keyIdx[i]); i++ }
        return found.sorted().take(n).map { words[it] }
    }

    /** Words at edit distance 1 (substitute / delete / insert / transpose), best first. */
    fun corrections(word: String, n: Int): List<String> {
        val w = norm(word); if (w.isEmpty()) return emptyList()
        val cand = HashMap<Int, Int>()          // idx -> score (lower is better)
        fun probe(c: String, bonus: Int) {
            val idx = normToIdx[c] ?: return
            var score = idx - bonus
            if (c.isNotEmpty() && w.isNotEmpty() && c[0] == w[0]) score -= 2000   // typos rarely hit the first letter
            cand.merge(idx, score) { a, b -> minOf(a, b) }
        }
        for (i in 0..w.length) {
            if (i < w.length) probe(w.removeRange(i, i + 1), 0)
            if (i < w.length - 1) probe(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2), 4000)   // transposition
            for (a in alphabet) {
                if (i < w.length) probe(w.substring(0, i) + a + w.substring(i + 1), 0)
                probe(w.substring(0, i) + a + w.substring(i), 0)
            }
        }
        return cand.entries.sortedBy { it.value }.map { it.key }.filter { norm(words[it]) != w }.take(n).map { words[it] }
    }

    companion object {
        fun normalize(lang: String, w: String): String {
            if (lang != "ar") return w.lowercase().replace('’', '\'')
            val sb = StringBuilder(w.length)
            for (ch in w) when (ch) {
                in 'ً'..'ٟ', 'ٰ', 'ـ' -> {}
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ى' -> sb.append('ي')
                'ة' -> sb.append('ه')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                else -> sb.append(ch)
            }
            return sb.toString()
        }
    }
}

/** Words the user typed repeatedly (or reverted an auto-correction for). Never corrected, offered as suggestions. */
class LearnedWords(private val load: () -> String, private val save: (String) -> Unit) {
    private val counts = HashMap<String, Int>().also { m ->
        load().lineSequence().forEach { l -> val t = l.lastIndexOf('\t'); if (t > 0) l.substring(t + 1).toIntOrNull()?.let { m[l.substring(0, t)] = it } }
    }
    fun count(w: String) = counts[w.lowercase()] ?: 0
    fun isKnown(w: String) = count(w) >= 2
    fun bump(w: String, by: Int = 1) {
        val k = w.lowercase(); if (k.length < 2 || k.length > 30) return
        counts[k] = (counts[k] ?: 0) + by
        if (counts.size > 3000) counts.entries.sortedBy { it.value }.take(500).forEach { counts.remove(it.key) }
        save(counts.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }
    fun words(prefix: String): List<String> = counts.entries.filter { it.value >= 2 && it.key.startsWith(prefix.lowercase()) && it.key != prefix.lowercase() }
        .sortedByDescending { it.value }.map { it.key }
}
