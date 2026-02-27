package com.omnicontrol.agent.data.model

import com.google.gson.annotations.SerializedName

data class StorageInfo(
    @SerializedName("internal")
    val internal: StorageVolume,

    @SerializedName("external_sd")
    val externalSd: StorageVolume?
)

data class StorageVolume(
    @SerializedName("total_bytes")
    val totalBytes: Long,

    @SerializedName("available_bytes")
    val availableBytes: Long,

    @SerializedName("used_bytes")
    val usedBytes: Long,

    @SerializedName("used_percent")
    val usedPercent: Float
)
