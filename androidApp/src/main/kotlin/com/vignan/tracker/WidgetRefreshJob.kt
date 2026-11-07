package com.vignan.tracker

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetRefreshJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        AppContextProvider.context = applicationContext
        gate.tryStart() // A job can also start in a fresh process.
        stopped = false
        work = scope.launch {
            var failed = false
            try {
                withContext(Dispatchers.IO) {
                    WidgetUpdater.refresh(applicationContext, params.extras.getBoolean("from_tap"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                Log.e("WidgetRefresh", "Refresh failed", e)
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    gate.finish()
                    if (!stopped) jobFinished(params, false)
                    if (failed || stopped) WidgetUpdater.updateAll(applicationContext)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        work?.cancel()
        return false // One accepted tap never queues a retry.
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 4101
        private val gate = WidgetRefreshGate()
        val isRefreshing: Boolean get() = gate.isRunning

        @Synchronized
        fun enqueue(context: Context, fromTap: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null || !gate.tryStart()) return
            try {
                val builder = JobInfo.Builder(JOB_ID, ComponentName(context, WidgetRefreshJob::class.java))
                    .setExtras(PersistableBundle().apply { putBoolean("from_tap", fromTap) })
                if (Build.VERSION.SDK_INT >= 31) builder.setExpedited(true)
                else builder.setOverrideDeadline(0)
                var result = scheduler.schedule(builder.build())
                if (result != JobScheduler.RESULT_SUCCESS && Build.VERSION.SDK_INT >= 31) {
                    result = scheduler.schedule(builder.setExpedited(false).setOverrideDeadline(0).build())
                }
                if (result != JobScheduler.RESULT_SUCCESS) gate.finish()
            } catch (e: RuntimeException) {
                gate.finish()
                Log.e("WidgetRefresh", "Could not schedule refresh", e)
            }
        }
    }
}
