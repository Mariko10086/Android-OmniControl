package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName
import com.omnicontrol.agent.data.model.AppStatusInfo
import com.omnicontrol.agent.data.model.DeviceInfo
import com.omnicontrol.agent.data.model.StorageInfo

data class RegisterPayload(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("device")
    val device: DeviceInfo,

    @SerializedName("storage")
    val storage: StorageInfo,

    @SerializedName("apps")
    val apps: List<AppStatusInfo>,

    @SerializedName("registered_at_ms")
    val registeredAtMs: Long = System.currentTimeMillis()
)
