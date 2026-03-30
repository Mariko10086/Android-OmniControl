package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName
import com.omnicontrol.agent.data.model.AppStatusInfo
import com.omnicontrol.agent.data.model.StorageInfo

data class RegisterPayload(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("device_name")
    val deviceName: String,

    @SerializedName("manufacturer")
    val manufacturer: String,

    @SerializedName("model")
    val model: String,

    @SerializedName("android_version")
    val androidVersion: String,

    @SerializedName("sdk_version")
    val sdkVersion: Int,

    @SerializedName("serial_number")
    val serialNumber: String,

    @SerializedName("imei")
    val imei: String,

    @SerializedName("ip_address")
    val ipAddress: String,

    @SerializedName("system_language")
    val systemLanguage: String,

    @SerializedName("file_write_check")
    val fileWriteCheck: Boolean,

    @SerializedName("storage")
    val storage: StorageInfo,

    @SerializedName("apps")
    val apps: List<AppStatusInfo>,

    @SerializedName("registered_at_ms")
    val registeredAtMs: Long = System.currentTimeMillis()
)
