package com.omnicontrol.agent.system

import android.app.ActivityManager
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
     * 需要 root 权限；非 root 环境下静默失败不影响功能。
     */
    suspend fun protectCurrentProcess() {
        val pid = android.os.Process.myPid()
        // oom_adj 在 Android 7+ 内核已废弃（只读），只写 oom_score_adj 即可
        ShellExecutor.exec("echo -1000 > /proc/$pid/oom_score_adj")
        Log.i(TAG, "Process $pid oom_score_adj=-1000 requested (needs root; silently ignored if unavailable)")
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
        if (isMqttServiceRunning(context)) {
            Log.d(TAG, "MqttService is running")
            return
        }
        Log.w(TAG, "MqttService not running, restarting...")
        com.omnicontrol.agent.mqtt.MqttService.start(context)
    }

    /**
     * 通过 ActivityManager 在 Java 层判断 MqttService 是否运行，
     * 避免依赖 dumpsys（非 root 环境下输出不可靠，易误判导致反复重启）。
     */
    @Suppress("DEPRECATION")
    private fun isMqttServiceRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val targetClass = com.omnicontrol.agent.mqtt.MqttService::class.java.name
        return try {
            // getRunningServices 在 Android 8+ 对第三方 App 只返回自身进程的服务，
            // 但因为我们就是在检查自身服务，这里完全够用。
            am.getRunningServices(50)?.any { it.service.className == targetClass } == true
        } catch (e: Exception) {
            Log.w(TAG, "isMqttServiceRunning check failed: ${e.message}")
            // 检查失败时保守处理，不触发重启
            true
        }
    }
}
