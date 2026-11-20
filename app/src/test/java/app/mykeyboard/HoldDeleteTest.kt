package app.mykeyboard

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class HoldDeleteTest {
    class Counter : KeyboardListener {
        var chars = 0; var words = 0; var other = 0
        override fun onKey(action: Action, text: String) { if (action == Action.BACKSPACE) chars++ else other++ }
        override fun onLongAction(action: Action) { if (action == Action.BACKSPACE) words++ }
        override fun onTool(tool: Tool) {}
        override fun onSuggestion(index: Int) {}
        override fun onStatusTap() {}
    }

    private fun setup(): Triple<KeyboardView, Counter, Key> {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val kv = KeyboardView(ctx, Prefs(ctx)); val c = Counter(); kv.listener = c
        kv.setLayoutState(LayoutState("ar"))
        kv.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        @Suppress("UNCHECKED_CAST")
        val rows = kv.javaClass.getDeclaredField("rows").apply { isAccessible = true }.get(kv) as List<Row>
        return Triple(kv, c, rows[2].keys.last())
    }

    private fun ev(kv: KeyboardView, a: Int, t0: Long, dt: Long, k: Key) =
        kv.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + dt, a, k.x + k.w / 2, k.y + k.h / 2, 0))

    @Test fun tapDeletesExactlyOne() {
        val (kv, c, k) = setup(); val t = SystemClock.uptimeMillis()
        ev(kv, MotionEvent.ACTION_DOWN, t, 0, k); ev(kv, MotionEvent.ACTION_UP, t, 80, k)
        ShadowLooper.idleMainLooper(1000, TimeUnit.MILLISECONDS)
        assertEquals(1, c.chars); assertEquals(0, c.words)
    }

    @Test fun holdKeepsDeletingFastThenWholeWords() {
        val (kv, c, k) = setup(); val t = SystemClock.uptimeMillis()
        ev(kv, MotionEvent.ACTION_DOWN, t, 0, k)
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        assertEquals("nothing repeats before the hold delay", 1, c.chars)
        ShadowLooper.idleMainLooper(1200, TimeUnit.MILLISECONDS)          // 1.5 s total
        assertTrue("fast repeat while held: ${c.chars}", c.chars in 10..40)
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS)          // 4.5 s total
        assertTrue("switches to whole words: ${c.words}", c.words >= 5)
        val soFar = c.chars + c.words
        ev(kv, MotionEvent.ACTION_UP, t, 4500, k)
        ShadowLooper.idleMainLooper(2000, TimeUnit.MILLISECONDS)
        assertEquals("stops as soon as the finger is lifted", soFar, c.chars + c.words)
    }
}
