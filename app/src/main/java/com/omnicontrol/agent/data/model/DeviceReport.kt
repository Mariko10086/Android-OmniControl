package com.omnicontrol.agent.data.model

import com.google.gson.annotations.SerializedName

/**
 * Top-level JSON envelope sent to POST /v1/devices/report on every work cycle.
 *
 * A single consolidated payload (device info + storage + app statuses) minimises
 * the number of HTTP connections at 70,000-device fleet scale compared to
 * separate endpoints for each data type.
 */
data class DeviceReport(
    @SerializedName("device")
    val device: DeviceInfo,

    @SerializedName("storage")
    val storage: StorageInfo,

    @SerializedName("apps")
    val apps: List<AppStatusInfo>,

    @SerializedName("reported_at_ms")
    val reportedAtMs: Long = System.currentTimeMillis(),

    @SerializedName("report_sequence")
    val reportSequence: Long
)
