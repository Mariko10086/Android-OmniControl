package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName

data class UpdateAckPayload(
    @SerializedName("task_uuid")
    val taskUuid: String,

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("ack_status")
    val ackStatus: String = "acknowledged"
)
