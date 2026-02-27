package com.omnicontrol.agent.network

import android.content.Context
import com.omnicontrol.agent.BuildConfig
import com.omnicontrol.agent.config.AppConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    /**
     * Builds an ApiService using the current server URL from AppConfig.
     * Called once per WorkManager run so that remote server URL changes take effect.
     */
    fun buildApiService(context: Context): ApiService {
        val baseUrl = AppConfig.getServerUrl(context)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(AppConfig.HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
