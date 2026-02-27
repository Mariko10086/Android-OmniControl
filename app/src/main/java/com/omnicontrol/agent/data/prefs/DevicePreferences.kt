package com.omnicontrol.agent.data.prefs

import android.content.Context
import java.util.UUID

/**
 * Manages the stable device identity and report sequence counter.
 *
 * Device ID is generated once on first launch using a random UUID and persisted.
 * Avoids hardware identifiers (IMEI/serial) which require privileged permissions
 * on Android 10+.
 */
class DevicePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_REPORT_SEQUENCE = "report_sequence"
    }

    fun getOrCreateDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
    }

    fun nextReportSequence(): Long {
        val next = prefs.getLong(KEY_REPORT_SEQUENCE, 0L) + 1L
        prefs.edit().putLong(KEY_REPORT_SEQUENCE, next).apply()
        return next
    }
}
