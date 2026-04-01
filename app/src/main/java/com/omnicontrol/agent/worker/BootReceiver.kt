package com.omnicontrol.agent.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.omnicontrol.agent.mqtt.MqttService
import com.omnicontrol.agent.system.ScreenKeepAwake
import com.omnicontrol.agent.system.SelfElevation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores the WorkManager schedule and restarts the MQTT service after device reboot.
 *
 * On many Chinese OEM ROMs (MIUI, EMUI, ColorOS) WorkManager periodic work and
 * foreground services do not survive reboot automatically. Explicitly handling
 * BOOT_COMPLETED is the safest approach for managed device fleets.
 *
 * MY_PACKAGE_REPLACED：APP 被更新安装后触发，此时需要：
 *   1. 清除 elevation_attempted 标记 → 若新版仍非系统 APP，OmniControlApp.checkAndElevateIfNeeded() 会重新尝试
 *   2. 重启 MqttService（安装替换后服务被停止）
 *   3. 重新调度 WorkManager 任务（替换安装会清除之前的 WorkManager 队列）
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "miui.intent.action.BOOT_COMPLETED" -> {
                MqttService.start(context)
                WorkerScheduler.scheduleAll(context)
                // Re-apply screen keep-awake settings — OEM ROMs may reset them on boot
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    ScreenKeepAwake.applySystemSettings()
                }
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // APP 更新安装完成：
                // 1. 清除 elevation 标记，允许新版本在非系统 APP 时重新尝试提权
                SelfElevation.resetElevationFlag(context)
                // 2. 重启服务和调度（替换安装后服务和 WorkManager 任务均被清除）
                MqttService.start(context)
                WorkerScheduler.scheduleAll(context)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    ScreenKeepAwake.applySystemSettings()
                }
            }
        }
    }
}
