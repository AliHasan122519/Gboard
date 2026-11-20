package app.mykeyboard

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.mykeyboard.update.UpdateInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class UpdateUiTest {
    private fun texts(v: View, out: MutableList<TextView> = ArrayList()): List<TextView> {
        if (v is TextView && v.visibility == View.VISIBLE) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    @Test fun newerVersionShowsBannerButtonAndDialog() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = Prefs(ctx)
        prefs.availableUpdate = UpdateInfo("v9.9.9", "9.9.9", "- something new", "https://raw.githubusercontent.com/o/r/b/x.apk", 10, "a".repeat(64)).toJson()
        prefs.lastUpdateCheck = System.currentTimeMillis()          // no network call in the test
        val a = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible().get()
        val all = texts(a.window.decorView).map { it.text.toString() }
        assertTrue("banner title: $all", all.any { it.contains("9.9.9") && (it.contains("New version") || it.contains("إصدار")) })
        assertTrue("update button", all.any { it == "Update now" || it == "تحديث الآن" })
        assertTrue("notes", all.any { it.contains("something new") })
        val d = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(d.isShowing)
        assertNotNull(d.getButton(AlertDialog.BUTTON_POSITIVE)); assertNotNull(d.getButton(AlertDialog.BUTTON_NEGATIVE))
    }

    @Test fun noUpdateShowsNoBanner() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = Prefs(ctx); prefs.availableUpdate = null; prefs.lastUpdateCheck = System.currentTimeMillis()
        val a = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible().get()
        assertFalse(texts(a.window.decorView).any { it.text.toString().contains("9.9.9") })
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }
}
