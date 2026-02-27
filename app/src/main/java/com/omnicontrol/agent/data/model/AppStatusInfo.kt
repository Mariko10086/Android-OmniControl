package com.omnicontrol.agent.data.model

import com.google.gson.annotations.SerializedName

data class AppStatusInfo(
    @SerializedName("package_name")
    val packageName: String,

    @SerializedName("installed")
    val installed: Boolean,

    @SerializedName("version_name")
    val versionName: String?,

    @SerializedName("version_code")
    val versionCode: Long?,

    @SerializedName("enabled")
    val enabled: Boolean?,

    @SerializedName("first_install_time_ms")
    val firstInstallTimeMs: Long?,

    @SerializedName("last_update_time_ms")
    val lastUpdateTimeMs: Long?
)
