package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName
import com.omnicontrol.agent.data.model.AppStatusInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppStatusPayload(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("timestamp")
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),

    @SerializedName("apps")
    val apps: List<AppStatusInfo>,

    @SerializedName("sequence")
    val sequence: Long
)
