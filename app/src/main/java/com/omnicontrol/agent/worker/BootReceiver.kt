package com.omnicontrol.agent.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores the WorkManager schedule after device reboot.
 *
 * On many Chinese OEM ROMs (MIUI, EMUI, ColorOS) WorkManager periodic work
 * does not survive reboot automatically. Explicitly rescheduling from
 * BOOT_COMPLETED is the safest approach for automation device fleets.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WorkerScheduler.schedule(context)
            }
        }
    }
}
