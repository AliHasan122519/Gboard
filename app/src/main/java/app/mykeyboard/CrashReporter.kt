package app.mykeyboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Records uncaught exceptions to a local file and opens [CrashActivity] (own process) with the full report,
 * so a crash is never silent: the text can be copied or shared with the developer. Nothing is sent anywhere.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}

object CrashReporter {
    fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    /** Stores a non-fatal error (a screen section or key that failed) so the app keeps running and the text can be read later. */
    fun record(c: Context, where: String, e: Throwable) {
        runCatching {
            val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
            val ver = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull()
            val f = file(c)
            val old = if (f.exists()) f.readText().take(6000) else ""
            f.writeText("My Keyboard error report (non-fatal)\nwhere: $where\ntime: ${java.util.Date()}\napp: $ver\n" +
                "device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n" +
                sw.toString().take(6000) + "\n----\n" + old)
        }
    }

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                val ver = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                val report = buildString {
                    append("My Keyboard crash report\n")
                    append("time: ${java.util.Date()}\nthread: ${t.name}\napp: $ver\n")
                    append("device: ${Build.MANUFACTURER} ${Build.MODEL}\nandroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n")
                    append(sw.toString().take(12000))
                }
                file(app).writeText(report)
                app.startActivity(Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            previous?.uncaughtException(t, e)
        }
    }
}
