package com.omnicontrol.agent.network

import android.content.Context
import com.omnicontrol.agent.BuildConfig
import com.omnicontrol.agent.config.AppConfig
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.network.auth.AuthApiService
import com.omnicontrol.agent.network.auth.DeviceApiService
import com.omnicontrol.agent.network.auth.JwtInterceptor
import com.omnicontrol.agent.network.auth.TokenAuthenticator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    /**
     * Builds an unauthenticated Retrofit service for auth endpoints
     * (POST /auth/login and POST /auth/refresh).
     * No JWT interceptor or authenticator is attached.
     */
    fun buildAuthApiService(context: Context): AuthApiService {
        return Retrofit.Builder()
            .baseUrl(AppConfig.getServerUrl(context))
            .client(buildBaseOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }

    /**
     * Builds an authenticated Retrofit service for device REST endpoints.
     * Attaches [JwtInterceptor] to proactively include the Bearer token and
     * [TokenAuthenticator] to silently refresh on 401 responses.
     */
    fun buildDeviceApiService(context: Context): DeviceApiService {
        val prefs = DevicePreferences(context)
        val authApiService = buildAuthApiService(context)
        val authenticatedClient = buildBaseOkHttpClient().newBuilder()
            .addInterceptor(JwtInterceptor(prefs))
            .authenticator(TokenAuthenticator(prefs, authApiService))
            .build()

        return Retrofit.Builder()
            .baseUrl(AppConfig.getServerUrl(context))
            .client(authenticatedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeviceApiService::class.java)
    }

    private fun buildBaseOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(AppConfig.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .build()
    }
}
