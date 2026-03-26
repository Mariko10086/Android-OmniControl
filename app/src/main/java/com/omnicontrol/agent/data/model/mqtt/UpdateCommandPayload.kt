package com.omnicontrol.agent.data.model.mqtt

import com.google.gson.annotations.SerializedName

/** Inbound message: server → device via omnicontrol/s2d/{device_id}/update_command */
data class UpdateCommandPayload(
    @SerializedName("task_uuid")
    val taskUuid: String,

    @SerializedName("package_name")
    val packageName: String,

    @SerializedName("version_name")
    val versionName: String?,

    @SerializedName("version_code")
    val versionCode: Long?,

    @SerializedName("apk_url")
    val apkUrl: String,

    @SerializedName("apk_md5")
    val apkMd5: String,

    @SerializedName("is_mandatory")
    val isMandatory: Boolean = false
)
