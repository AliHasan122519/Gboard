package app.mykeyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The keyboard must look like the reference screenshot whatever font is selected: only letter shapes may differ. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class ReferenceFidelityTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun render(fontId: String, lang: String = "ar", shift: Shift = Shift.OFF): Pair<Bitmap, List<Row>> {
        val prefs = Prefs(ctx)
        val fonts = FontRepository(ctx)
        val kv = KeyboardView(ctx, prefs)
        kv.setPalette(Palette.DARK)
        kv.setTypeface(fonts.typeface(fonts.find(fontId), 400))
        kv.setLayoutState(LayoutState(lang, Page.LETTERS, shift))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val b = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(b))
        @Suppress("UNCHECKED_CAST")
        val rows = kv.javaClass.getDeclaredField("rows").apply { isAccessible = true }.get(kv) as List<Row>
        return b to rows
    }

    /** bbox of light pixels inside a key, as fractions of the key height: (top, bottom, height). */
    private fun glyph(b: Bitmap, k: Key): Triple<Float, Float, Float>? {
        var minY = Int.MAX_VALUE; var maxY = -1
        for (y in k.y.toInt() until (k.y + k.h).toInt()) for (x in k.x.toInt() until (k.x + k.w).toInt()) {
            val p = b.getPixel(x, y)
            if (Color.red(p) > 150 && Color.green(p) > 150) { if (y < minY) minY = y; if (y > maxY) maxY = y }
        }
        if (maxY < 0) return null
        return Triple((minY - k.y) / k.h, (maxY - k.y) / k.h, (maxY - minY + 1) / k.h)
    }

    @Test fun everyFontMatchesReferenceAlefHeightAndBaseline() {
        val fonts = FontRepository(ctx).builtInFonts()
        val report = StringBuilder()
        for (f in fonts) {
            if (f.hasArabic) {
                val (b, rows) = render(f.id)
                val g = glyph(b, rows[1].keys[5])!!               // "ا"
                report.append(String.format("%-14s alef height %.3f (ref 0.405)  bottom %.3f (ref 0.727)\n", f.id, g.third, g.second))
                assertEquals("${f.id} alef height", 49f / 121f, g.third, 0.03f)
                assertEquals("${f.id} baseline", 88f / 121f, g.second, 0.03f)
            } else {
                val (b, rows) = render(f.id, "en", Shift.ON)
                val g = glyph(b, rows[1].keys[5])!!               // "H"
                report.append(String.format("%-14s H height %.3f (target 0.360)  bottom %.3f (target 0.665)\n", f.id, g.third, g.second))
                assertEquals("${f.id} cap height", 0.36f, g.third, 0.03f)
                assertEquals("${f.id} baseline", 0.665f, g.second, 0.03f)
            }
        }
        java.io.File("/tmp/claude-0/fidelity.txt").writeText(report.toString())
    }

    /** Space label, pill labels, comma/period, hint digits, icons: identical pixels with any two fonts. */
    @Test fun nonLetterPartsAreIdenticalAcrossFonts() {
        val (a, rowsA) = render("cairo")
        val (c, _) = render("amiri")
        val bottom = rowsA[3].keys
        for (k in bottom) for (y in k.y.toInt() until (k.y + k.h).toInt()) for (x in k.x.toInt() until (k.x + k.w).toInt())
            assertEquals("bottom row pixel differs at $x,$y (${k.action})", a.getPixel(x, y), c.getPixel(x, y))
        // toolbar icons too
        for (y in 0 until rowsA[0].keys[0].y.toInt() - 5) for (x in 0 until a.width) assertEquals(a.getPixel(x, y), c.getPixel(x, y))
    }
}
