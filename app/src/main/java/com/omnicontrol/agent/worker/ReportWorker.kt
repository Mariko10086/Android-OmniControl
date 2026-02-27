package com.omnicontrol.agent.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnicontrol.agent.collector.AppStatusCollector
import com.omnicontrol.agent.collector.DeviceInfoCollector
import com.omnicontrol.agent.collector.StorageInfoCollector
import com.omnicontrol.agent.config.AppConfig
import com.omnicontrol.agent.data.model.DeviceReport
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.network.NetworkClient
import com.omnicontrol.agent.network.NetworkResult
import com.omnicontrol.agent.network.ReportResponse

/**
 * WorkManager worker that collects all device metrics and reports them to the server.
 *
 * Execution flow:
 * 1. Collect device info, storage stats, and app statuses.
 * 2. POST consolidated DeviceReport to the server.
 * 3. On success, process any server-pushed config updates (packages, interval, URL).
 * 4. Return Result.success(), Result.retry(), or Result.failure() accordingly.
 *
 * WorkManager provides automatic exponential backoff when Result.retry() is returned.
 */
class ReportWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val report = buildReport()
            val networkResult = submitReport(report)
            when (networkResult) {
                is NetworkResult.Success -> {
                    handleServerConfig(networkResult.data)
                    Result.success()
                }
                is NetworkResult.Error -> {
                    // 4xx: client-side error, retrying won't help
                    if (networkResult.code in 400..499) Result.failure()
                    else Result.retry()
                }
                is NetworkResult.Exception -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun buildReport(): DeviceReport {
        val prefs = DevicePreferences(context)
        val targetPackages = AppConfig.getTargetPackages(context)
        return DeviceReport(
            device = DeviceInfoCollector(context).collect(),
            storage = StorageInfoCollector(context).collect(),
            apps = AppStatusCollector(context).collect(targetPackages),
            reportSequence = prefs.nextReportSequence()
        )
    }

    private suspend fun submitReport(report: DeviceReport): NetworkResult<ReportResponse> {
        return try {
            val api = NetworkClient.buildApiService(context)
            val response = api.postDeviceReport(report)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error(response.code(), response.message())
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }

    private fun handleServerConfig(response: ReportResponse) {
        response.updatedPackages?.let { packages ->
            if (packages.isNotEmpty()) {
                AppConfig.updateTargetPackages(context, packages)
            }
        }
        response.updatedServerUrl?.let { url ->
            if (url.isNotBlank()) {
                AppConfig.updateServerUrl(context, url)
            }
        }
        response.nextIntervalMinutes?.let { intervalMinutes ->
            val currentInterval = AppConfig.getReportIntervalMinutes(context)
            if (intervalMinutes != currentInterval && intervalMinutes >= 15L) {
                AppConfig.updateReportInterval(context, intervalMinutes)
                WorkerScheduler.schedule(context, intervalMinutes)
            }
        }
    }

    companion object {
        const val WORK_NAME = "omnicontrol_report_worker"
    }
}
