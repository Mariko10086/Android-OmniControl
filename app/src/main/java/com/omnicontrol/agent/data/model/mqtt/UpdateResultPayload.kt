package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName

data class UpdateResultPayload(
    @SerializedName("task_uuid")
    val taskUuid: String,

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("success")
    val success: Boolean,

    @SerializedName("error_message")
    val errorMessage: String?,

    @SerializedName("installed_version_code")
    val installedVersionCode: Long?
)
