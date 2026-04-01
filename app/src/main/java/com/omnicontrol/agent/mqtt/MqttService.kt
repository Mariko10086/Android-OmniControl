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
import com.omnicontrol.agent.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that maintains the persistent MQTT connection.
 *
 * Lifecycle:
 * - Started by [OmniControlApp.onCreate] and [BootReceiver].
 * - [START_STICKY] ensures the system restarts it if killed under memory pressure.
 *   重要：START_STICKY 重启时系统只调 onStartCommand，不调 onCreate，
 *   因此所有需要重新初始化的状态必须在 onStartCommand 中重置，而非依赖成员变量初始值。
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

    /**
     * serviceScope 每次 onCreate 新建，onDestroy 取消。
     * START_STICKY 重启时系统重新走 onCreate → 新的 scope，避免旧 cancelled scope 上 launch 失败。
     */
    private lateinit var serviceScope: CoroutineScope
    private lateinit var mqttManager: MqttManager
    private lateinit var updateInstaller: UpdateInstaller

    /**
     * 防止同一次 Service 生命周期内 onStartCommand 被多次调用时重复初始化连接。
     * 每次 onCreate 重置为 false，使 START_STICKY 重启后能正确重新连接。
     */
    @Volatile
    private var connectionInitialized = false

    /** 监听 connectionState 的协程 Job，确保只有一个存活 */
    private var connectionStateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // 每次 Service 实例创建时重建 scope 和状态，保证 START_STICKY 重启后干净初始化
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        connectionInitialized = false
        createNotificationChannel()
        updateInstaller = UpdateInstaller(applicationContext)
        FileLogger.i(TAG, "MqttService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        if (!serviceScope.isActive) {
            // 极端情况：scope 已经被 cancel（理论上不应到这里，防御性处理）
            FileLogger.w(TAG, "serviceScope is cancelled, rebuilding...")
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            connectionInitialized = false
        }

        serviceScope.launch {
            if (connectionInitialized) return@launch
            connectionInitialized = true
            initializeConnection()
        }
        return START_STICKY
    }

    private suspend fun initializeConnection() {
        val prefs = DevicePreferences(applicationContext)
        val authApiService = NetworkClient.buildAuthApiService(applicationContext)
        val authManager = AuthManager(prefs, authApiService)

        FileLogger.i(TAG, "Starting authentication...")
        if (!authManager.ensureAuthenticated()) {
            FileLogger.e(TAG, "Authentication failed — cannot establish MQTT connection")
            stopSelf()
            return
        }
        FileLogger.i(TAG, "Authentication succeeded")

        val deviceId = prefs.getOrCreateDeviceId()
        FileLogger.i(TAG, "Device ID: $deviceId")

        mqttManager = MqttManagerHolder.init(applicationContext)

        try {
            mqttManager.connect(
                host = AppConfig.getMqttBrokerHost(applicationContext),
                port = AppConfig.getMqttBrokerPort(applicationContext),
                clientId = deviceId
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "MQTT connect exception: ${e.message}")
            // HiveMQ automatic reconnect will handle retries; we don't stop the service.
            return
        }

        // 监听连接状态变化：每次连接成功（含重连）都重新发注册消息
        // 服务端 register handler 是 upsert 幂等操作，重复注册安全
        connectionStateJob?.cancel()
        connectionStateJob = serviceScope.launch {
            mqttManager.connectionState.collect { state ->
                if (state == MqttConnectionState.Connected) {
                    FileLogger.i(TAG, "MQTT connected/reconnected, sending registration for $deviceId")
                    sendRegistration(deviceId)
                }
            }
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
                    FileLogger.w(TAG, "update_ack publish failed: ${e.message}")
                }

                // 2. Download, verify, install and report result
                val result = updateInstaller.install(command)
                try {
                    mqttManager.publishQos1(MqttTopics.updateResult(deviceId), result)
                } catch (e: Exception) {
                    FileLogger.w(TAG, "update_result publish failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendRegistration(deviceId: String) {
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
            FileLogger.i(TAG, "Device registered: $deviceId")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Registration publish failed: ${e.message}")
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
        FileLogger.i(TAG, "MqttService onDestroy")
        serviceScope.cancel()
        connectionStateJob = null
        if (::mqttManager.isInitialized) {
            mqttManager.disconnect()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
