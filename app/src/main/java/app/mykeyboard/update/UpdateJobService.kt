package app.mykeyboard.update

import android.app.job.JobParameters
import android.app.job.JobService
import app.mykeyboard.Prefs

/** Daily background check. Only talks to GitHub, then posts a notification (or just flags the update inside the app). */
class UpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        if (!Prefs(this).autoUpdate) return false
        Thread {
            val m = UpdateManager(this)
            val r = m.check()
            if (r is CheckResult.Available) m.notifyAvailable(r.info)
            jobFinished(params, r is CheckResult.Failed)   // retry later on network failure
        }.start()
        return true
    }
    override fun onStopJob(params: JobParameters) = true
}
