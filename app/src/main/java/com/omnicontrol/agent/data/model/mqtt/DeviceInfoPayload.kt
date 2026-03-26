package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName
import com.omnicontrol.agent.data.model.DeviceInfo
import com.omnicontrol.agent.data.model.StorageInfo

data class DeviceInfoPayload(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("device")
    val device: DeviceInfo,

    @SerializedName("storage")
    val storage: StorageInfo,

    @SerializedName("reported_at_ms")
    val reportedAtMs: Long = System.currentTimeMillis(),

    @SerializedName("sequence")
    val sequence: Long
)
