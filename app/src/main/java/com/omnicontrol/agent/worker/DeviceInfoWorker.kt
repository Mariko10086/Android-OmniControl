package com.omnicontrol.agent.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnicontrol.agent.collector.DeviceInfoCollector
import com.omnicontrol.agent.collector.StorageInfoCollector
import com.omnicontrol.agent.data.model.mqtt.DeviceInfoPayload
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.mqtt.MqttManagerHolder
import com.omnicontrol.agent.mqtt.MqttService
import com.omnicontrol.agent.mqtt.MqttTopics

/**
 * Periodic worker that publishes current device info and storage stats via MQTT QoS 1.
 * Scheduled at the interval stored in [AppConfig] (default 15 minutes).
 */
class DeviceInfoWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "omnicontrol_device_info_worker"
        private const val TAG = "DeviceInfoWorker"
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

            val payload = DeviceInfoPayload(
                deviceId = deviceId,
                device = DeviceInfoCollector(context).collect(),
                storage = StorageInfoCollector(context).collect(),
                sequence = prefs.nextReportSequence()
            )
            mqttManager.publishQos1(MqttTopics.deviceInfo(deviceId), payload)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "DeviceInfo publish failed: ${e.message}")
            Result.retry()
        }
    }
}
