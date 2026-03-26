package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName
import com.omnicontrol.agent.data.model.AppStatusInfo

data class AppStatusPayload(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("apps")
    val apps: List<AppStatusInfo>,

    @SerializedName("reported_at_ms")
    val reportedAtMs: Long = System.currentTimeMillis(),

    @SerializedName("sequence")
    val sequence: Long
)
