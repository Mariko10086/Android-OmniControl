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
 * Schedules the three periodic background workers:
 * - [HeartbeatWorker]  — every 5 minutes, QoS 0
 * - [DeviceInfoWorker] — every [intervalMinutes] (default 15), QoS 1
 * - [AppStatusWorker]  — once per day, QoS 1
 *
 * Uses [ExistingPeriodicWorkPolicy.UPDATE] for DeviceInfoWorker so server-pushed
 * interval changes take effect immediately. HeartbeatWorker and AppStatusWorker
 * use [ExistingPeriodicWorkPolicy.KEEP] to avoid unnecessary rescheduling.
 *
 * A random 0–5 minute jitter is added to the DeviceInfoWorker initial delay to
 * prevent fleet-wide thundering-herd bursts.
 */
object WorkerScheduler {

    fun scheduleAll(context: Context, deviceInfoIntervalMinutes: Long? = null) {
        scheduleHeartbeat(context)
        scheduleDeviceInfo(context, deviceInfoIntervalMinutes)
        scheduleAppStatus(context)
    }

    fun scheduleHeartbeat(context: Context) {
        val req = PeriodicWorkRequestBuilder<HeartbeatWorker>(5, TimeUnit.MINUTES)
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HeartbeatWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    fun scheduleDeviceInfo(context: Context, intervalMinutes: Long? = null) {
        val interval = intervalMinutes ?: AppConfig.getReportIntervalMinutes(context)
        val jitterMs = (Math.random() * 5 * 60 * 1000).toLong()
        val req = PeriodicWorkRequestBuilder<DeviceInfoWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(networkConstraint())
            .setInitialDelay(jitterMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DeviceInfoWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun scheduleAppStatus(context: Context) {
        val req = PeriodicWorkRequestBuilder<AppStatusWorker>(24, TimeUnit.HOURS)
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AppStatusWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    private fun networkConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
