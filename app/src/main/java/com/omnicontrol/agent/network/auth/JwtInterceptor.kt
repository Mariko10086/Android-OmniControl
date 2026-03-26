package com.omnicontrol.agent.network.auth

import com.omnicontrol.agent.data.prefs.DevicePreferences
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Proactively injects the JWT access token into every outgoing request.
 * If no token is available the request is forwarded without an Authorization header;
 * [TokenAuthenticator] will handle the resulting 401.
 */
class JwtInterceptor(private val prefs: DevicePreferences) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = prefs.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
