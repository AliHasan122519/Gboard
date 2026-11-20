package app.mykeyboard.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.mykeyboard.MainActivity
import app.mykeyboard.Prefs
import app.mykeyboard.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class CheckResult {
    data class Available(val info: UpdateInfo) : CheckResult()
    object UpToDate : CheckResult()
    data class Failed(val reason: String) : CheckResult()
}

/**
 * In-app updater. Update source = the latest *published* GitHub release of [repo]; the release must carry an .apk asset.
 * Nothing is installed silently: Android always asks the user to confirm, and Android itself refuses an APK that is
 * not signed with the same key as the installed app.
 */
class UpdateManager(private val context: Context) {
    private val ctx = context.applicationContext
    private val prefs = Prefs(ctx)
    private val repo get() = ctx.getString(R.string.update_repo)

    val currentVersion: String
        get() = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    fun check(): CheckResult {
        return try {
            val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000; readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MyKeyboard-Updater")
            }
            try {
                when (conn.responseCode) {
                    200 -> {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val info = ReleaseParser.parse(body)
                        prefs.lastUpdateCheck = System.currentTimeMillis()
                        if (info != null && Versions.isNewer(info.version, currentVersion)) {
                            prefs.availableUpdate = info.toJson(); CheckResult.Available(info)
                        } else { prefs.availableUpdate = null; CheckResult.UpToDate }
                    }
                    404 -> { prefs.lastUpdateCheck = System.currentTimeMillis(); prefs.availableUpdate = null; CheckResult.UpToDate } // no release yet
                    else -> CheckResult.Failed("HTTP ${conn.responseCode}")
                }
            } finally { conn.disconnect() }
        } catch (e: Exception) {
            CheckResult.Failed(e.javaClass.simpleName)
        }
    }

    /** Downloads the APK to the app cache, verifying size, SHA-256 (if known), package name and signing key. */
    fun download(info: UpdateInfo, onProgress: (Long, Long) -> Unit): Result<File> = runCatching {
        require(info.apkUrl.startsWith("https://")) { "insecure url" }
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "update-${info.version}.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000; setRequestProperty("User-Agent", "MyKeyboard-Updater")
        }
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            conn.inputStream.use { input -> out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    o.write(buf, 0, n); md.update(buf, 0, n); done += n
                    check(done <= MAX_BYTES) { "too large" }
                    onProgress(done, total)
                }
            } }
            if (info.size > 0) check(done == info.size) { "size mismatch" }
            info.sha256?.let { expected -> check(md.digest().joinToString("") { "%02x".format(it) } == expected) { "checksum mismatch" } }
        } finally { conn.disconnect() }
        verifyApk(out)
        out
    }.onFailure { File(ctx.cacheDir, "updates").deleteRecursively() }

    /** The APK must be this app and signed with the same key (Android would reject it later anyway; this gives a clear message). */
    private fun verifyApk(apk: File) {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("not an apk")
        check(archive.packageName == ctx.packageName) { "wrong package" }
        check(signers(archive).isNotEmpty() && signers(archive) == signers(pm.getPackageInfo(ctx.packageName, flags))) { "different signing key" }
    }

    @Suppress("DEPRECATION")
    private fun signers(p: android.content.pm.PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        return sigs?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }?.toSet() ?: emptySet()
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands the verified APK to the system installer. Android shows its own confirmation dialog. */
    fun install(apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            apk.inputStream().use { input -> s.openWrite("update.apk", 0, apk.length()).use { out -> input.copyTo(out); s.fsync(out) } }
            val intent = Intent(ctx, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
        }
    }

    // ---------------------------------------------------------------- background check
    fun scheduleDaily() {
        val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (!prefs.autoUpdate) { js.cancel(JOB_ID); return }
        if (js.getPendingJob(JOB_ID) != null) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJobService::class.java))
                .setPeriodic(24L * 60 * 60 * 1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true).build(),
        )
    }

    fun notifyAvailable(info: UpdateInfo) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(ctx.getString(R.string.update_available_title))
            .setContentText(ctx.getString(R.string.update_available_text, info.version))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(NOTIF_ID, n) }   // silently skipped if the notification permission was denied
    }

    companion object {
        const val JOB_ID = 7301
        const val NOTIF_ID = 7302
        const val CHANNEL = "updates"
        const val MAX_BYTES = 120L * 1024 * 1024
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val prefs = Prefs(context)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> { prefs.lastInstallError = ""; prefs.availableUpdate = null }
            else -> prefs.lastInstallError = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
        }
    }
    companion object { const val ACTION = "app.mykeyboard.INSTALL_RESULT" }
}
