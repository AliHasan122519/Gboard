package app.mykeyboard

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import java.io.File
import java.io.RandomAccessFile

class FontEntry(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val group: String,
    /** Asset path for built-in fonts, absolute file path for user fonts. */
    val path: String,
    val builtIn: Boolean,
    val variable: Boolean,
    val minWeight: Int,
    val maxWeight: Int,
    val hasArabic: Boolean,
    val license: String,
    /** For families shipped as separate static files (e.g. Marlik): weight -> asset path. */
    val weightFiles: Map<Int, String> = emptyMap(),
) {
    fun displayName(arabicUi: Boolean) = if (arabicUi && nameAr.isNotEmpty()) nameAr else nameEn
}

/**
 * Built-in fonts come from assets/fonts/catalog.json; fonts the user adds from the keyboard are copied into
 * filesDir/fonts and discovered by scanning that folder, so they survive restarts without any extra database.
 */
class FontRepository(private val context: Context) {
    private val cache = HashMap<String, Typeface>()
    private var builtIns: List<FontEntry>? = null

    val userDir: File get() = File(context.filesDir, "fonts").also { it.mkdirs() }

    fun builtInFonts(): List<FontEntry> = builtIns ?: loadCatalog().also { builtIns = it }

    fun userFonts(): List<FontEntry> =
        (userDir.listFiles { f -> f.isFile && f.extension.lowercase() in EXT } ?: emptyArray())
            .sortedBy { it.name.lowercase() }
            .mapNotNull { f ->
                val wght = readWeightAxis(f)
                val name = f.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
                runCatching { Typeface.createFromFile(f) }.getOrNull() ?: return@mapNotNull null
                FontEntry(
                    id = "user:" + f.name, nameAr = name, nameEn = name, group = "user", path = f.absolutePath,
                    builtIn = false, variable = wght != null,
                    minWeight = wght?.first ?: 400, maxWeight = wght?.second ?: 400, hasArabic = true, license = "user",
                )
            }

    fun all(): List<FontEntry> = userFonts() + builtInFonts()

    fun find(id: String): FontEntry? = all().firstOrNull { it.id == id }

    fun typeface(entry: FontEntry?, weight: Int): Typeface {
        val e = entry ?: builtInFonts().firstOrNull { it.id == DEFAULT_ID } ?: return Typeface.DEFAULT
        val w = if (e.variable) weight.coerceIn(e.minWeight, e.maxWeight) else 0
        if (e.weightFiles.isNotEmpty()) {
            val nearest = e.weightFiles.keys.minByOrNull { kotlin.math.abs(it - weight) } ?: 400
            return cache.getOrPut("${e.id}@$nearest") {
                runCatching { Typeface.createFromAsset(context.assets, e.weightFiles.getValue(nearest)) }.getOrElse { Typeface.DEFAULT }
            }
        }
        val key = "${e.id}@$w"
        return cache.getOrPut(key) { create(e, w) }
    }

    private fun create(e: FontEntry, weight: Int): Typeface = runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            val b = if (e.builtIn) Typeface.Builder(context.assets, e.path) else Typeface.Builder(File(e.path))
            if (e.variable) b.setFontVariationSettings("'wght' $weight")
            b.build() ?: throw IllegalStateException("null typeface")
        } else {
            if (e.builtIn) Typeface.createFromAsset(context.assets, e.path) else Typeface.createFromFile(e.path)
        }
    }.getOrElse { Typeface.DEFAULT }

    /** Copies a font chosen with the system file picker into the app. Returns the new id or null if unusable. */
    fun importFont(uri: Uri): String? {
        val displayName = queryName(uri) ?: "font-${System.currentTimeMillis()}.ttf"
        var base = displayName.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}_ .-]"), "_").trim().ifEmpty { "font" }
        val ext = displayName.substringAfterLast('.', "ttf").lowercase().let { if (it in EXT) it else "ttf" }
        val tmp = File(context.cacheDir, "import-${System.nanoTime()}.$ext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_FONT_BYTES) return null
                        out.write(buf, 0, n)
                    }
                }
            } ?: return null
            if (!looksLikeFont(tmp)) return null
            // make sure Android can actually load it before keeping it
            val ok = runCatching { Typeface.createFromFile(tmp) }.getOrNull()
            if (ok == null || ok == Typeface.DEFAULT) return null
            var target = File(userDir, "$base.$ext"); var i = 2
            while (target.exists()) { target = File(userDir, "$base-$i.$ext"); i++ }
            tmp.copyTo(target)
            return "user:" + target.name
        } catch (_: Exception) {
            return null
        } finally {
            tmp.delete()
        }
    }

    fun deleteUserFont(id: String): Boolean {
        if (!id.startsWith("user:")) return false
        val name = id.removePrefix("user:")
        if (name.contains('/') || name.contains("..")) return false
        cache.keys.removeAll { it.startsWith("$id@") }
        return File(userDir, name).delete()
    }

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun loadCatalog(): List<FontEntry> {
        val json = context.assets.open("fonts/catalog.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val w = o.optJSONArray("wght")
            val wf = o.optJSONObject("weights")
            val files = wf?.keys()?.asSequence()?.associate { it.toInt() to wf.getString(it) } ?: emptyMap()
            FontEntry(
                id = o.getString("id"), nameAr = o.optString("nameAr"), nameEn = o.getString("nameEn"),
                group = o.optString("group"), path = o.getString("file"), builtIn = true,
                variable = o.optBoolean("variable") || files.isNotEmpty(),
                minWeight = w?.optDouble(0)?.toInt() ?: 400, maxWeight = w?.optDouble(2)?.toInt() ?: 400,
                hasArabic = o.optBoolean("arabic", true), license = o.optString("license"),
                weightFiles = files,
            )
        }
    }

    companion object {
        const val DEFAULT_ID = "dubai"
        const val MAX_FONT_BYTES = 40L * 1024 * 1024
        val EXT = setOf("ttf", "otf")

        private fun looksLikeFont(f: File): Boolean = runCatching {
            RandomAccessFile(f, "r").use { r ->
                if (r.length() < 64) return false
                val tag = r.readInt()
                tag == 0x00010000 || tag == 0x4F54544F /* OTTO */ || tag == 0x74727565 /* true */
            }
        }.getOrDefault(false)

        /** Reads the sfnt table directory and returns (min,max) of the 'wght' axis if the font is variable. */
        fun readWeightAxis(f: File): Pair<Int, Int>? = runCatching {
            RandomAccessFile(f, "r").use { r ->
                r.seek(4); val numTables = r.readUnsignedShort()
                r.seek(12)
                var fvarOffset = -1L
                for (i in 0 until numTables) {
                    val tag = ByteArray(4).also { r.readFully(it) }
                    r.readInt(); val off = r.readInt().toLong() and 0xFFFFFFFFL; r.readInt()
                    if (String(tag, Charsets.US_ASCII) == "fvar") { fvarOffset = off; break }
                }
                if (fvarOffset < 0) return@use null
                r.seek(fvarOffset + 4)
                val axesOffset = r.readUnsignedShort(); r.readUnsignedShort()
                val axisCount = r.readUnsignedShort(); val axisSize = r.readUnsignedShort()
                for (i in 0 until axisCount) {
                    r.seek(fvarOffset + axesOffset + i.toLong() * axisSize)
                    val tag = ByteArray(4).also { r.readFully(it) }
                    val mn = r.readInt() / 65536f; r.readInt(); val mx = r.readInt() / 65536f
                    if (String(tag, Charsets.US_ASCII) == "wght") return@use mn.toInt() to mx.toInt()
                }
                null
            }
        }.getOrNull()
    }
}
