package com.omnicontrol.agent.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnicontrol.agent.data.model.mqtt.HeartbeatPayload
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.mqtt.MqttManagerHolder
import com.omnicontrol.agent.mqtt.MqttService
import com.omnicontrol.agent.mqtt.MqttTopics

/**
 * Periodic worker that publishes a QoS 0 heartbeat every 5 minutes.
 *
 * If the MQTT connection is down, it attempts to restart [MqttService] and
 * returns [Result.retry] so WorkManager reschedules the publish.
 */
class HeartbeatWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "omnicontrol_heartbeat_worker"
        private const val TAG = "HeartbeatWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val mqttManager = MqttManagerHolder.get(context)
            if (!mqttManager.isConnected()) {
                Log.w(TAG, "MQTT not connected — restarting MqttService")
                MqttService.start(context)
                return Result.retry()
            }
            val deviceId = DevicePreferences(context).getOrCreateDeviceId()
            mqttManager.publishQos0(MqttTopics.heartbeat(deviceId), HeartbeatPayload(deviceId))
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Heartbeat failed: ${e.message}")
            Result.retry()
        }
    }
}
