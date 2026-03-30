package com.omnicontrol.agent.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.omnicontrol.agent.mqtt.MqttService
import com.omnicontrol.agent.system.ScreenKeepAwake
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
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "miui.intent.action.BOOT_COMPLETED",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                MqttService.start(context)
                WorkerScheduler.scheduleAll(context)
                // Re-apply screen keep-awake settings — OEM ROMs may reset them on boot
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    ScreenKeepAwake.applySystemSettings()
                }
            }
        }
    }
}
