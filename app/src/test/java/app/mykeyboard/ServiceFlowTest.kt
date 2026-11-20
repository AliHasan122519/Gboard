package app.mykeyboard

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** Drives the real keyboard service with a real text field: this is what exposed the "delete stops after 2 characters" bug. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class ServiceFlowTest {
    private class Rig(val service: KeyboardService, val kv: KeyboardView, val field: EditText) {
        @Suppress("UNCHECKED_CAST")
        val rows get() = kv.javaClass.getDeclaredField("rows").apply { isAccessible = true }.get(kv) as List<Row>
        fun key(r: Int, action: Action) = rows[r].keys.first { it.action == action }
        fun keyLabel(label: String) = rows.flatMap { it.keys }.first { it.label == label }
        fun send(a: Int, t0: Long, dt: Long, k: Key) = kv.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + dt, a, k.x + k.w / 2, k.y + k.h / 2, 0))
        fun tap(k: Key) { val t = SystemClock.uptimeMillis(); send(MotionEvent.ACTION_DOWN, t, 0, k); send(MotionEvent.ACTION_UP, t, 60, k); ShadowLooper.idleMainLooper(120, TimeUnit.MILLISECONDS) }
    }

    private fun boot(initial: String, lang: String = "ar"): Rig {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        Prefs(ctx).lang = lang
        val s = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = s.onCreateInputView() as ViewGroup
        root.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val field = EditText(ctx).apply { setText(initial); setSelection(initial.length) }
        InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply { isAccessible = true }.set(s, object : BaseInputConnection(field, true) { override fun getEditable() = field.editableText })
        s.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        val kv = root.getChildAt(0) as KeyboardView
        kv.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        return Rig(s, kv, field)
    }

    @Test fun tappingAKeyTypesIt() {
        val r = boot("")
        r.tap(r.keyLabel("ب")); r.tap(r.keyLabel("ا"))
        assertEquals("با", r.field.text.toString())
    }

    @Test fun singleTapDeletesOneCharacter() {
        val r = boot("abcdef")
        r.tap(r.key(2, Action.BACKSPACE))
        assertEquals("abcde", r.field.text.toString())
    }

    @Test fun holdingDeleteKeepsDeletingUntilTheFingerLifts() {
        val text = "the quick brown fox jumps over the lazy dog again and again"
        val r = boot(text)
        val k = r.key(2, Action.BACKSPACE)
        val t = SystemClock.uptimeMillis()
        r.send(MotionEvent.ACTION_DOWN, t, 0, k)
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS)
        assertEquals("one character right away", text.length - 1, r.field.text.length)
        ShadowLooper.idleMainLooper(1400, TimeUnit.MILLISECONDS)
        val afterHold = r.field.text.length
        assertTrue("keeps going while held (left $afterHold of ${text.length})", afterHold < text.length - 15)
        r.send(MotionEvent.ACTION_UP, t, 1700, k)
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS)
        assertEquals("stops when released", afterHold, r.field.text.length)
    }

    @Test fun longHoldEventuallyDeletesWholeWords() {
        val r = boot("one two three four five six seven eight nine ten eleven twelve thirteen fourteen")
        val k = r.key(2, Action.BACKSPACE); val t = SystemClock.uptimeMillis()
        r.send(MotionEvent.ACTION_DOWN, t, 0, k)
        ShadowLooper.idleMainLooper(2600, TimeUnit.MILLISECONDS)
        r.send(MotionEvent.ACTION_UP, t, 2600, k)
        assertTrue("whole words deleted: '${r.field.text}'", r.field.text.length < 25)
    }

    @Test fun twoFingersAtOnceBothRegister() {
        val r = boot("")
        val a = r.keyLabel("ب"); val b = r.keyLabel("ت"); val t = SystemClock.uptimeMillis()
        fun ev(act: Int, idx: Int, k: Key) = MotionEvent.obtain(t, t + 10, act or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply { x = k.x + k.w / 2; y = k.y + k.h / 2 }), 0, 0, 1f, 1f, 0, 0, 0, 0)
        r.send(MotionEvent.ACTION_DOWN, t, 0, a); r.send(MotionEvent.ACTION_UP, t, 40, a)
        r.send(MotionEvent.ACTION_DOWN, t + 50, 0, b); r.send(MotionEvent.ACTION_UP, t + 50, 40, b)
        assertEquals("بت", r.field.text.toString())
    }

    private fun waitForDictionary(r: Rig) {
        val f = KeyboardService::class.java.getDeclaredField("engine").apply { isAccessible = true }
        val end = System.currentTimeMillis() + 30000
        while (f.get(r.service) == null && System.currentTimeMillis() < end) { Thread.sleep(100); ShadowLooper.idleMainLooper() }
        assertNotNull("dictionary loaded", f.get(r.service))
    }

    @Test fun autoCorrectFixesTypoOnSpaceAndBackspaceUndoesIt() {
        val r = boot("", "en")
        waitForDictionary(r)
        r.service.onKey(Action.LANG, "")                                   // starts in English already; toggle back if needed
        if (r.rows[0].keys[0].label != "q") r.service.onKey(Action.LANG, "")
        for (c in "teh") r.service.onKey(Action.TEXT, c.toString())
        r.service.onKey(Action.SPACE, "")
        assertEquals("the ", r.field.text.toString())
        r.service.onKey(Action.BACKSPACE, "")
        assertEquals("teh", r.field.text.toString())                       // one backspace undoes the correction
        r.service.onKey(Action.SPACE, "")
        assertEquals("teh ", r.field.text.toString())                      // and it is not corrected again
    }
}
