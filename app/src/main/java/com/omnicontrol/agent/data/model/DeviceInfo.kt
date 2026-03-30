package com.omnicontrol.agent.data.model

import android.os.Build
import com.google.gson.annotations.SerializedName

data class DeviceInfo(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("device_name")
    val deviceName: String = Build.MODEL,

    @SerializedName("manufacturer")
    val manufacturer: String = Build.MANUFACTURER,

    @SerializedName("model")
    val model: String = Build.MODEL,

    @SerializedName("android_version")
    val androidVersion: String = Build.VERSION.RELEASE,

    @SerializedName("sdk_version")
    val sdkVersion: Int = Build.VERSION.SDK_INT,

    @SerializedName("serial_number")
    val serialNumber: String = "",

    @SerializedName("imei")
    val imei: String = "",

    @SerializedName("ip_address")
    val ipAddress: String = "",

    @SerializedName("system_language")
    val systemLanguage: String = "",

    @SerializedName("file_write_check")
    val fileWriteCheck: Boolean = false
)
