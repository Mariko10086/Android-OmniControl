package com.omnicontrol.agent.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.omnicontrol.agent.mqtt.MqttService

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
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                MqttService.start(context)
                WorkerScheduler.scheduleAll(context)
            }
        }
    }
}
