package app.mykeyboard

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KeyboardLogicTest {
    private val fonts = File("src/main/assets/fonts")

    @Test fun arabicLayoutMatchesReference() {
        val rows = Layouts.rows(LayoutState("ar"))
        assertEquals("ضصثقفغعهخحج", rows[0].keys.joinToString("") { it.label })
        assertEquals("شسيبلاتنمكط", rows[1].keys.joinToString("") { it.label })
        assertEquals("ذءؤرىةوزظد", rows[2].keys.filter { it.action == Action.TEXT }.joinToString("") { it.label })
        assertEquals(Action.BACKSPACE, rows[2].keys.last().action)
        assertEquals(listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"), rows[0].keys.take(10).map { it.hint })
        assertNull(rows[0].keys[10].hint)
        assertEquals("؟٣٢١", rows[3].keys[0].label)
        assertEquals(6, rows[3].keys.size)
    }

    @Test fun englishLayoutAndShift() {
        val rows = Layouts.rows(LayoutState("en"))
        assertEquals("qwertyuiop", rows[0].keys.joinToString("") { it.label })
        assertEquals("asdfghjkl", rows[1].keys.joinToString("") { it.label })
        assertEquals(Action.SHIFT, rows[2].keys.first().action)
        val up = Layouts.rows(LayoutState("en", shift = Shift.ON))
        assertEquals("QWERTYUIOP", up[0].keys.joinToString("") { it.label })
    }

    @Test fun symbolPagesHaveFourRows() {
        for (p in listOf(Page.SYM1, Page.SYM2)) for (l in listOf("ar", "en")) assertEquals(4, Layouts.rows(LayoutState(l, p)).size)
    }

    /** Pixel geometry measured from the reference screenshot (1079 px wide, 18 px status strip removed). */
    @Test fun geometryMatchesScreenshot() {
        val rows = Layouts.rows(LayoutState("ar"))
        KeyGeometry.layout(rows, 1079f, 157f, 121f, 30f)
        val row1 = rows[0].keys
        assertEquals(12f, row1.first().x, 1f)
        assertEquals(83f, row1.first().w, 1f)
        assertEquals(1067f, row1.last().x + row1.last().w, 1f)
        assertEquals(97f, row1[1].x - row1[0].x, 1f)
        val b = rows[3].keys
        assertEquals(146f, b[0].w, 3f)
        assertEquals(92f, b[1].w, 3f)
        assertEquals(414f, b[3].w, 5f)
        assertEquals(146f, b[5].w, 3f)
        assertEquals(1067f, b[5].x + b[5].w, 1f)
        // row tops in the screenshot (minus 18 px strip): 157 / 308 / 459 / 611
        assertEquals(listOf(157f, 308f, 459f), rows.take(3).map { it.keys[0].y.let { y -> Math.round(y).toFloat() } })
        assertEquals(611f, b[0].y, 6f)
    }

    @Test fun catalogMatchesAssets() {
        val json = File(fonts, "catalog.json").readText()
        val ids = Regex("\"id\": \"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toList()
        assertTrue("dubai" in ids && "marlik" in ids && "cairo" in ids)
        assertEquals(35, ids.size)
        Regex("\"file\": \"([^\"]+)\"").findAll(json).forEach { assertTrue(it.groupValues[1], File("src/main/assets", it.groupValues[1]).isFile) }
        (100..900 step 100).forEach { assertTrue(File(fonts, "marlik_$it.ttf").isFile) }
    }

    @Test fun everyFontHasKnownGroupAndGroupsAreOrdered() {
        val json = File(fonts, "catalog.json").readText()
        val groups = Regex("\"group\": \"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toList()
        val known = listOf("modern", "kufi", "naskh", "calligraphy", "display", "fun", "latin")
        assertTrue(groups.all { it in known })
        // catalog is stored grouped, in display order
        assertEquals(groups.sortedBy { known.indexOf(it) }, groups)
        assertEquals(known.toSet(), groups.toSet())
    }

    @Test fun variableWeightAxisParsing() {
        val axis = FontRepository.readWeightAxis(File(fonts, "cairo.ttf"))
        assertNotNull(axis); assertEquals(200, axis!!.first); assertEquals(1000, axis.second)
        assertNull(FontRepository.readWeightAxis(File(fonts, "dubai.ttf")))
        assertNull(FontRepository.readWeightAxis(File(fonts, "tajawal.ttf")))
    }

    @Test fun tashkeelDetection() {
        assertTrue(Layouts.isTashkeel("\u064E")); assertFalse(Layouts.isTashkeel("ب")); assertFalse(Layouts.isTashkeel("."))
    }
}
