package com.omnicontrol.agent.collector

import android.content.Context
import com.omnicontrol.agent.data.model.DeviceInfo
import com.omnicontrol.agent.data.prefs.DevicePreferences

class DeviceInfoCollector(private val context: Context) {

    private val prefs = DevicePreferences(context)

    fun collect(): DeviceInfo {
        return DeviceInfo(
            deviceId = prefs.getOrCreateDeviceId()
            // All other fields use Build.* defaults defined in the data class
        )
    }
}
