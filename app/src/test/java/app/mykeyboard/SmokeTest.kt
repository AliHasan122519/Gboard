package app.mykeyboard

import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches the real activity / service code paths on the Android framework to catch start-up crashes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 30, 31, 33, 34, 35], qualifiers = "w411dp-h900dp-xxhdpi")
class SmokeTest {
    @Test fun mainActivityLaunches() {
        val a = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible().get()
        assertNotNull(a.window.decorView)
    }

    @Test fun keyboardCreatesAndShowsView() {
        val s = Robolectric.buildService(KeyboardService::class.java).create().get()
        val v = s.onCreateInputView()
        assertNotNull(v)
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        assertTrue(v.measuredHeight > 0)
    }

    @Test fun panelsBuild() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = Prefs(ctx)
        val fonts = FontRepository(ctx)
        val host = object : PanelHost {
            override fun closePanel() {}; override fun insert(text: String) {}; override fun backspace() {}
            override fun openFontImport() {}; override fun openSettings() {}; override fun openPanel(tool: Tool) {}
            override fun cycleTheme() {}; override fun showImePicker() {}; override fun fontChanged() {}; override fun toast(resId: Int) {}
        }
        FontPanel(ctx, Palette.DARK, prefs, fonts, host)
        EmojiPanel(ctx, Palette.DARK, prefs, host)
        ClipboardPanel(ctx, Palette.DARK, prefs, ClipboardStore(ctx), host)
        MenuPanel(ctx, Palette.DARK, prefs, host)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class CrashReportTest {
    @Test fun recordedErrorShowsBannerAndCrashScreenOpens() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        CrashReporter.record(ctx, "test", IllegalStateException("boom"))
        assertTrue(CrashReporter.file(ctx).readText().contains("boom"))
        val a = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible().get()
        assertNotNull(a.window.decorView)
        val c = Robolectric.buildActivity(CrashActivity::class.java).create().start().resume().visible().get()
        assertNotNull(c.window.decorView)
        CrashReporter.file(ctx).delete()
    }
}
