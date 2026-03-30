package com.omnicontrol.agent.network.auth

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

/** Wrapper matching the server's { "code", "message", "data": T } envelope. */
data class ApiResponse<T>(
    @SerializedName("code")    val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data: T?
)

/** Token payload nested inside the "data" field. */
data class TokenData(
    @SerializedName("accessToken")  val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String? = null
)

/** Unauthenticated endpoints — no JWT required. */
interface AuthApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<TokenData>>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<ApiResponse<TokenData>>
}
