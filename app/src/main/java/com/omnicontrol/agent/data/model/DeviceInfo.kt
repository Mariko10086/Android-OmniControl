package com.omnicontrol.agent.data.model

import android.os.Build
import com.google.gson.annotations.SerializedName

data class DeviceInfo(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("model")
    val model: String = Build.MODEL,

    @SerializedName("manufacturer")
    val manufacturer: String = Build.MANUFACTURER,

    @SerializedName("android_version")
    val androidVersion: String = Build.VERSION.RELEASE,

    @SerializedName("sdk_int")
    val sdkInt: Int = Build.VERSION.SDK_INT,

    @SerializedName("brand")
    val brand: String = Build.BRAND,

    @SerializedName("product")
    val product: String = Build.PRODUCT
)
