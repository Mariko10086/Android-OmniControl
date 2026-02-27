package com.omnicontrol.agent.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.omnicontrol.agent.config.AppConfig
import java.util.concurrent.TimeUnit

/**
 * Schedules (or reschedules) the periodic ReportWorker.
 *
 * Uses ExistingPeriodicWorkPolicy.UPDATE so that subsequent calls
 * (reboot, server-pushed interval change) replace the existing work
 * without creating duplicates.
 *
 * A random initial delay of 0-5 minutes is applied to spread the first
 * requests across 70,000 devices and avoid a thundering herd on the server.
 */
object WorkerScheduler {

    fun schedule(context: Context, intervalMinutes: Long? = null) {
        val interval = intervalMinutes ?: AppConfig.getReportIntervalMinutes(context)

        // Spread initial requests: 0-5 minute random jitter
        val jitterMs = (Math.random() * 5 * 60 * 1000).toLong()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ReportWorker>(
            repeatInterval = interval,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(jitterMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReportWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
