package app.mykeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ClipItem(val text: String, val pinned: Boolean, val time: Long)

/** Small persistent clipboard history (stored only on this device, never sent anywhere). */
class ClipboardStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("clipboard", Context.MODE_PRIVATE)

    fun items(): List<ClipItem> {
        val arr = runCatching { JSONArray(sp.getString("items", "[]")) }.getOrDefault(JSONArray())
        val list = (0 until arr.length()).mapNotNull {
            runCatching { arr.getJSONObject(it).let { o -> ClipItem(o.getString("t"), o.optBoolean("p"), o.optLong("ts")) } }.getOrNull()
        }
        return list.sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.time })
    }

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_LEN) return
        val cur = items().toMutableList()
        val existing = cur.firstOrNull { it.text == t }
        cur.removeAll { it.text == t }
        cur.add(ClipItem(t, existing?.pinned ?: false, System.currentTimeMillis()))
        save(trim(cur))
    }

    fun togglePin(text: String) = save(items().map { if (it.text == text) ClipItem(it.text, !it.pinned, it.time) else it })
    fun remove(text: String) = save(items().filter { it.text != text })
    fun clearUnpinned() = save(items().filter { it.pinned })

    private fun trim(l: List<ClipItem>): List<ClipItem> {
        val pinned = l.filter { it.pinned }
        val rest = l.filter { !it.pinned }.sortedByDescending { it.time }.take(MAX_ITEMS)
        return pinned + rest
    }

    private fun save(l: List<ClipItem>) {
        val arr = JSONArray()
        l.forEach { arr.put(JSONObject().put("t", it.text).put("p", it.pinned).put("ts", it.time)) }
        sp.edit().putString("items", arr.toString()).apply()
    }

    companion object { const val MAX_ITEMS = 30; const val MAX_LEN = 2000 }
}
