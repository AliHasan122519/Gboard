package app.mykeyboard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File

/** Renders a message as a PNG in the chosen font, so the typeface is visible in any chat app that accepts images. */
object TextImage {
    class Preset(val bg: Int, val fg: Int)
    val PRESETS = listOf(
        Preset(0xFF1C1E2A.toInt(), 0xFFE1E3F2.toInt()),   // dark
        Preset(0xFFFFFFFF.toInt(), 0xFF1B1B21.toInt()),   // light
        Preset(0xFF4A4DB0.toInt(), 0xFFFFFFFF.toInt()),   // indigo
        Preset(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),   // black
    )

    fun dir(ctx: Context) = File(ctx.cacheDir, "stickers").also { it.mkdirs() }

    /** Picks the image type the target app accepts from the keyboard (PNG preferred). null = the app takes no images. */
    fun chooseMime(types: Array<String>?): String? {
        if (types == null) return null
        fun ok(m: String) = types.any { it == m || it == "image/*" }
        return listOf("image/png", "image/webp", "image/jpeg").firstOrNull { ok(it) }
    }

    fun extFor(mime: String) = when (mime) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }

    fun render(ctx: Context, text: String, tf: Typeface, preset: Preset, width: Int = 900, mime: String = "image/png"): File {
        val pad = 56f
        val body = text.trim().take(1500)
        var size = 68f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; color = preset.fg }
        var layout: StaticLayout
        while (true) {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(body, 0, body.length, paint, (width - 2 * pad).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 1.2f).setIncludePad(true).build()
            if (layout.height <= 2600 || size <= 26f) break
            size -= 6f
        }
        val h = (layout.height + 2 * pad).toInt()
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = preset.bg }
        val radius = if (mime == "image/png") 56f else 0f          // JPEG has no transparency: square corners there
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), h.toFloat()), radius, radius, bgPaint)
        c.translate(pad, pad)
        layout.draw(c)
        // keep the cache small: drop old images
        dir(ctx).listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24L * 3600 * 1000 }?.forEach { it.delete() }
        val f = File(dir(ctx), "message-${System.currentTimeMillis()}.${extFor(mime)}")
        val fmt = when (mime) {
            "image/jpeg" -> Bitmap.CompressFormat.JPEG
            "image/webp" -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.PNG
        }
        f.outputStream().use { bmp.compress(fmt, 95, it) }
        bmp.recycle()
        return f
    }

    fun uriFor(ctx: Context, f: File): Uri = Uri.parse("content://${ctx.packageName}.stickers/${f.name}")
}

/** Serves the rendered images to the app the user is chatting in (read-only, only files in our sticker cache). */
class StickerProvider : ContentProvider() {
    private fun fileFor(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        if (!Regex("[A-Za-z0-9._-]+\\.(png|jpg|webp)").matches(name)) return null
        val f = File(TextImage.dir(context!!), name)
        return if (f.isFile) f else null
    }

    override fun onCreate() = true
    override fun getType(uri: Uri) = when (uri.lastPathSegment?.substringAfterLast('.')) { "jpg" -> "image/jpeg"; "webp" -> "image/webp"; else -> "image/png" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = fileFor(uri) ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf(f.name, f.length())) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("read only")
        val f = fileFor(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
