package com.omnicontrol.agent.network.auth

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

data class DeviceResponse(
    @SerializedName("id")            val id: Long,
    @SerializedName("deviceId")      val deviceId: String,
    @SerializedName("deviceName")    val deviceName: String?,
    @SerializedName("manufacturer")  val manufacturer: String?,
    @SerializedName("model")         val model: String?,
    @SerializedName("androidVersion") val androidVersion: String?,
    @SerializedName("sdkVersion")    val sdkVersion: Int?,
    @SerializedName("ipAddress")     val ipAddress: String?,
    @SerializedName("status")        val status: String?,
    @SerializedName("lastOnlineAt")  val lastOnlineAt: String?,
    @SerializedName("registeredAt")  val registeredAt: String?
)

data class DeviceAppResponse(
    @SerializedName("id")                    val id: Long,
    @SerializedName("deviceId")              val deviceId: String,
    @SerializedName("packageName")           val packageName: String,
    @SerializedName("installedVersionName")  val installedVersionName: String?,
    @SerializedName("installedVersionCode")  val installedVersionCode: Long?,
    @SerializedName("updateStatus")          val updateStatus: String?,
    @SerializedName("lastReportedAt")        val lastReportedAt: String?
)

/** Authenticated endpoints — requires JWT Bearer token. */
interface DeviceApiService {

    @GET("devices/{deviceId}")
    suspend fun getDevice(@Path("deviceId") deviceId: String): Response<DeviceResponse>

    @GET("devices/{deviceId}/apps")
    suspend fun getDeviceApps(@Path("deviceId") deviceId: String): Response<List<DeviceAppResponse>>
}
