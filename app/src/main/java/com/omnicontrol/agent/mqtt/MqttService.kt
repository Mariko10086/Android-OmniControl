package com.omnicontrol.agent.mqtt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omnicontrol.agent.R
import com.omnicontrol.agent.auth.AuthManager
import com.omnicontrol.agent.collector.AppStatusCollector
import com.omnicontrol.agent.collector.DeviceInfoCollector
import com.omnicontrol.agent.collector.StorageInfoCollector
import com.omnicontrol.agent.config.AppConfig
import com.omnicontrol.agent.data.model.mqtt.RegisterPayload
import com.omnicontrol.agent.data.model.mqtt.UpdateAckPayload
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.network.NetworkClient
import com.omnicontrol.agent.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that maintains the persistent MQTT connection.
 *
 * Lifecycle:
 * - Started by [OmniControlApp.onCreate] and [BootReceiver].
 * - [START_STICKY] ensures the system restarts it if killed under memory pressure.
 * - Stopped only when the app is explicitly uninstalled or the service is disabled.
 */
class MqttService : Service() {

    companion object {
        private const val TAG = "MqttService"
        private const val NOTIF_CHANNEL_ID = "omnicontrol_mqtt"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MqttService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MqttService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var mqttManager: MqttManager
    private lateinit var updateInstaller: UpdateInstaller

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        updateInstaller = UpdateInstaller(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        serviceScope.launch {
            if (::mqttManager.isInitialized && mqttManager.isConnected()) return@launch
            initializeConnection()
        }
        return START_STICKY
    }

    private suspend fun initializeConnection() {
        val prefs = DevicePreferences(applicationContext)
        val authApiService = NetworkClient.buildAuthApiService(applicationContext)
        val authManager = AuthManager(prefs, authApiService)

        if (!authManager.ensureAuthenticated()) {
            Log.e(TAG, "Authentication failed — cannot establish MQTT connection")
            stopSelf()
            return
        }

        val deviceId = prefs.getOrCreateDeviceId()
        mqttManager = MqttManagerHolder.init(applicationContext)

        try {
            mqttManager.connect(
                host = AppConfig.getMqttBrokerHost(applicationContext),
                port = AppConfig.getMqttBrokerPort(applicationContext),
                clientId = deviceId
            )
        } catch (e: Exception) {
            Log.e(TAG, "MQTT connect exception: ${e.message}")
            // HiveMQ automatic reconnect will handle retries; we don't stop the service.
            return
        }

        if (!prefs.isRegistered()) {
            sendRegistration(deviceId, prefs)
        }

        mqttManager.subscribeToUpdateCommands(deviceId) { command ->
            serviceScope.launch {
                // 1. Acknowledge immediately
                val ack = UpdateAckPayload(
                    taskUuid = command.taskUuid,
                    deviceId = deviceId
                )
                try {
                    mqttManager.publishQos1(MqttTopics.updateAck(deviceId), ack)
                } catch (e: Exception) {
                    Log.w(TAG, "update_ack publish failed: ${e.message}")
                }

                // 2. Download, verify, install and report result
                val result = updateInstaller.install(command)
                try {
                    mqttManager.publishQos1(MqttTopics.updateResult(deviceId), result)
                } catch (e: Exception) {
                    Log.w(TAG, "update_result publish failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendRegistration(deviceId: String, prefs: DevicePreferences) {
        val packages = AppConfig.getTargetPackages(applicationContext)
        val deviceInfo = DeviceInfoCollector(applicationContext).collect()
        val payload = RegisterPayload(
            deviceId = deviceId,
            deviceName = deviceInfo.deviceName,
            manufacturer = deviceInfo.manufacturer,
            model = deviceInfo.model,
            androidVersion = deviceInfo.androidVersion,
            sdkVersion = deviceInfo.sdkVersion,
            serialNumber = deviceInfo.serialNumber,
            imei = deviceInfo.imei,
            ipAddress = deviceInfo.ipAddress,
            systemLanguage = deviceInfo.systemLanguage,
            fileWriteCheck = deviceInfo.fileWriteCheck,
            storage = StorageInfoCollector(applicationContext).collect(),
            apps = AppStatusCollector(applicationContext).collect(packages)
        )
        try {
            mqttManager.publishQos1(MqttTopics.register(deviceId), payload)
            prefs.markRegistered()
            Log.i(TAG, "Device registered: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Registration publish failed: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "OmniControl Agent",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the device connected to the management server"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("OmniControl Agent")
            .setContentText("Connected to management server")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (::mqttManager.isInitialized) {
            mqttManager.disconnect()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
