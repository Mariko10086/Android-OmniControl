package com.omnicontrol.agent.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnicontrol.agent.collector.AppStatusCollector
import com.omnicontrol.agent.config.AppConfig
import com.omnicontrol.agent.data.model.mqtt.AppStatusPayload
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.mqtt.MqttManagerHolder
import com.omnicontrol.agent.mqtt.MqttService
import com.omnicontrol.agent.mqtt.MqttTopics

/**
 * Periodic worker that publishes the monitored app status list once per day via MQTT QoS 1.
 * Can also be triggered as a one-time request when a package change is detected.
 */
class AppStatusWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "omnicontrol_app_status_worker"
        private const val TAG = "AppStatusWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val mqttManager = MqttManagerHolder.get(context)
            if (!mqttManager.isConnected()) {
                Log.w(TAG, "MQTT not connected — restarting MqttService")
                MqttService.start(context)
                return Result.retry()
            }
            val prefs = DevicePreferences(context)
            val deviceId = prefs.getOrCreateDeviceId()
            val packages = AppConfig.getTargetPackages(context)

            val payload = AppStatusPayload(
                deviceId = deviceId,
                apps = AppStatusCollector(context).collect(packages),
                sequence = prefs.nextReportSequence()
            )
            mqttManager.publishQos1(MqttTopics.appStatus(deviceId), payload)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "AppStatus publish failed: ${e.message}")
            Result.retry()
        }
    }
}
