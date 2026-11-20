package app.mykeyboard

import app.mykeyboard.text.LearnedWords
import app.mykeyboard.text.SuggestionEngine
import app.mykeyboard.text.TextStyle
import app.mykeyboard.text.WordDict
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class TypingAssistTest {
    companion object {
        lateinit var dicts: Map<String, WordDict>
        @BeforeClass @JvmStatic fun load() {
            dicts = listOf("ar", "en").associateWith { l -> WordDict(l).also { d -> File("src/main/assets/dict/$l.txt").useLines { d.load(it) } } }
        }
    }

    private var store = ""
    private fun engine() = SuggestionEngine(dicts, LearnedWords({ store }, { store = it }))

    @Test fun dictionariesLoaded() {
        assertTrue(dicts.getValue("en").size > 30000); assertTrue(dicts.getValue("ar").size > 30000)
        assertTrue(dicts.getValue("en").contains("hello")); assertTrue(dicts.getValue("ar").contains("مرحبا"))
        assertFalse("no profanity in the lists", dicts.getValue("en").contains("fuck"))
    }

    @Test fun englishTyposAreCorrected() {
        val e = engine()
        assertEquals("the", e.autoCorrect("I saw teh", "en")?.replacement)
        assertEquals("receive", e.autoCorrect("please recieve", "en")?.replacement)
        assertEquals("because", e.autoCorrect("bcause", "en")?.replacement)
        assertEquals("The", e.autoCorrect("Teh", "en")?.replacement)             // keeps the capital
    }

    @Test fun validWordsAndNamesAreLeftAlone() {
        val e = engine()
        for (w in listOf("hello", "world", "the", "because")) assertNull(w, e.autoCorrect("say $w", "en"))
        assertNull("capitalised mid-sentence = probably a name", e.autoCorrect("my friend Zaynab", "en"))
        assertNull("acronyms", e.autoCorrect("the NASAs", "en"))
        assertNull("too short", e.autoCorrect("xq", "en"))
    }

    @Test fun arabicSuggestionsAndCorrections() {
        val e = engine()
        assertNull(e.autoCorrect("هذا مرحبا", "ar"))
        assertNull("alef/hamza variants are not typos", e.autoCorrect("انا", "ar"))
        assertNull("teh marbuta/heh variants are not typos", e.autoCorrect("مدرسه", "ar"))
        val c = e.autoCorrect("ذهبت الى المدرسسة", "ar")
        assertNotNull(c); assertEquals("المدرسسة", c!!.original)
        assertTrue(e.suggest("السل", "ar").isNotEmpty())
    }

    @Test fun suggestionStrip() {
        val e = engine()
        val s = e.suggest("hel", "en")
        assertTrue(s.toString(), s.size in 1..3); assertTrue(s.any { it.startsWith("hel") })
        assertTrue("empty after a space", e.suggest("hello ", "en").isEmpty())
        val typo = e.suggest("teh", "en")
        assertEquals("the", typo[0]); assertEquals("teh", typo[1])                   // fix first, what you typed second
        assertTrue(e.suggest("12345", "en").isEmpty())
    }

    @Test fun capitalI() {
        val e = engine()
        assertEquals("I", e.capitalizeI("and i", "en")?.replacement)
        assertEquals("I'm", e.capitalizeI("so i'm", "en")?.replacement)
        assertNull(e.capitalizeI("hi", "en")); assertNull(e.capitalizeI("i", "ar"))
    }

    @Test fun learningAndUndo() {
        val e = engine()
        assertEquals("the", e.autoCorrect("said teh", "en")?.replacement)
        e.rejectCorrection("teh")                                   // user pressed backspace to undo the fix
        assertNull("never corrected again", e.autoCorrect("said teh", "en"))
        e.learn("zorbly", "en")
        assertNotNull("one use is not enough to trust", e.autoCorrect("a zorbly", "en") ?: "ok")
        e.learn("zorbly", "en")
        assertNull(e.autoCorrect("a zorbly", "en"))
        assertTrue(e.suggest("zorb", "en").contains("zorbly"))
        assertTrue(store.contains("zorbly"))
    }

    @Test fun wordDeletion() {
        fun left(before: String) = before.dropLast(KeyboardService.wordDeleteLength(before))
        assertEquals("hello ", left("hello world"))
        assertEquals("hello ", left("hello world  "))                 // trailing spaces go with the word
        assertEquals("مرحبا ", left("مرحبا بالعالم"))
        assertEquals("كتب ", left("كتب مَدرسَة"))                      // keeps diacritics attached to the word
        assertEquals("what", left("what?!"))                           // a run of punctuation is its own step
        assertEquals("don't ", left("don't stop"))
        assertEquals("hi ", left("hi 😀😀"))                           // emoji run, surrogate pairs stay whole
        assertEquals("", left("word"))
        assertEquals(1, KeyboardService.wordDeleteLength(" "))
    }

    @Test fun imageTypeFollowsWhatTheAppAccepts() {
        assertEquals("image/png", TextImage.chooseMime(arrayOf("image/gif", "image/png", "image/webp")))
        assertEquals("image/webp", TextImage.chooseMime(arrayOf("image/gif", "image/webp")))
        assertEquals("image/jpeg", TextImage.chooseMime(arrayOf("image/jpeg")))
        assertEquals("image/png", TextImage.chooseMime(arrayOf("image/*")))
        assertNull(TextImage.chooseMime(arrayOf("image/gif", "text/plain"))); assertNull(TextImage.chooseMime(null))
    }

    @Test fun unicodeStyles() {
        val styles = TextStyle.load(File("src/main/assets/styles.json").readText())
        fun st(id: String) = styles.first { it.id == id }
        assertEquals("𝐇𝐞𝐥𝐥𝐨 𝟏𝟐", st("bold").apply("Hello 12"))
        assertEquals("ℂ𝕒𝕥", st("double").apply("Cat"))
        assertEquals("Ⓐⓑⓒ", st("circled").apply("Abc"))
        assertEquals("مرحبا", st("bold").apply("مرحبا"))               // Arabic untouched by Latin styles
        assertEquals("كـتـابـة", st("kashida").apply("كتابة").replace("ـا", "ـا"))
        assertEquals("a̲b̲ c̲", st("underline").apply("ab c"))
        assertEquals("كـ", st("kashida").typed("ك")); assertEquals("ا", st("kashida").typed("ا"))
        assertTrue(styles.size >= 14)
    }
}
