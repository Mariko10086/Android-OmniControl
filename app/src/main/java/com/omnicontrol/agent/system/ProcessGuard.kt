package com.omnicontrol.agent.system

import android.content.Context
import android.util.Log
import com.omnicontrol.agent.shell.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 进程保护：
 * 1. 调整 OOM adj，降低被系统杀死的概率
 * 2. 启动看门狗，定期检查 MqttService 是否存活
 */
object ProcessGuard {

    private const val TAG = "ProcessGuard"
    private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L  // 5 分钟

    /**
     * 将当前进程的 oom_score_adj 调整为最低，提升存活优先级。
     * 需要 root 权限。
     */
    suspend fun protectCurrentProcess() {
        try {
            val pid = android.os.Process.myPid()
            ShellExecutor.exec("echo -17 > /proc/$pid/oom_adj")
            ShellExecutor.exec("echo -1000 > /proc/$pid/oom_score_adj")
            Log.i(TAG, "Process $pid oom_adj set to minimum")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set oom_adj: ${e.message}")
        }
    }

    /**
     * 启动看门狗协程，定期检查 MqttService 是否在运行，若未运行则拉起。
     */
    fun startWatchdog(context: Context, scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                try {
                    ensureMqttServiceRunning(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Watchdog error: ${e.message}")
                }
            }
        }
    }

    private fun ensureMqttServiceRunning(context: Context) {
        val serviceName = "com.omnicontrol.agent/com.omnicontrol.agent.mqtt.MqttService"
        val output = ShellExecutor.execWithResult("dumpsys activity services $serviceName")
        if (output.isNullOrBlank() || !output.contains("ServiceRecord")) {
            Log.w(TAG, "MqttService not found, restarting...")
            com.omnicontrol.agent.mqtt.MqttService.start(context)
        } else {
            Log.d(TAG, "MqttService is running")
        }
    }
}
