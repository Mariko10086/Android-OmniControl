package com.omnicontrol.agent.network

import com.google.gson.annotations.SerializedName
import com.omnicontrol.agent.data.model.DeviceReport
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class ReportResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("next_interval_minutes")
    val nextIntervalMinutes: Long?,

    @SerializedName("updated_packages")
    val updatedPackages: List<String>?,

    @SerializedName("updated_server_url")
    val updatedServerUrl: String?
)

interface ApiService {

    /**
     * Submits the consolidated device status report.
     * The server may respond with updated configuration values.
     */
    @POST("devices/report")
    suspend fun postDeviceReport(
        @Body report: DeviceReport
    ): Response<ReportResponse>
}
