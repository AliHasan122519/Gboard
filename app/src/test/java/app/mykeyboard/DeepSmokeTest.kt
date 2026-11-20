package app.mykeyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Runs the keyboard end to end with real drawing (native graphics) and real fonts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class DeepSmokeTest {
    private fun layoutAndDraw(v: View, w: Int = 1080): Bitmap {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val b = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        return b
    }

    private fun touch(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test fun keyboardServiceFullFlow() {
        val ctrl = Robolectric.buildService(KeyboardService::class.java).create()
        val s = ctrl.get()
        val v = s.onCreateInputView() as ViewGroup
        layoutAndDraw(v)
        s.onStartInputView(EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT }, false)
        val b = layoutAndDraw(v)
        assertTrue(b.width > 0)
        // tap a few places on the key area and tool strip
        for ((x, y) in listOf(100f to 300f, 540f to 450f, 900f to 600f, 540f to 700f, 70f to 60f, 450f to 60f)) touch(v, x, y)
        // open every panel through the tool strip
        for (tool in Tool.values()) { s.onTool(tool); layoutAndDraw(v) }
        s.onTool(Tool.FONTS); layoutAndDraw(v)
        s.closePanel()
        // numeric and email fields
        s.onStartInputView(EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }, false); layoutAndDraw(v)
        s.onStartInputView(EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }, false); layoutAndDraw(v)
        File("/tmp/claude-0/shot.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun everyBuiltInFontLoadsAndDraws() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val fonts = FontRepository(ctx)
        val prefs = Prefs(ctx)
        val kv = KeyboardView(ctx, prefs)
        for (f in fonts.builtInFonts()) {
            for (w in listOf(300, 500, 900)) {
                val tf = fonts.typeface(f, w)
                assertNotNull(f.id, tf)
                kv.setTypeface(tf)
                kv.setLayoutState(LayoutState("ar")); layoutAndDraw(kv)
                kv.setLayoutState(LayoutState("en", Page.LETTERS, Shift.ON)); layoutAndDraw(kv)
            }
        }
    }

    @Test fun mainActivityDraws() {
        val a = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible().get()
        layoutAndDraw(a.window.decorView)
    }
}
