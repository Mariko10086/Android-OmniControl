package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName

data class HeartbeatPayload(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("ts_ms")
    val tsMs: Long = System.currentTimeMillis()
)
