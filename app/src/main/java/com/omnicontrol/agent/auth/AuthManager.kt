package com.omnicontrol.agent.auth

import android.util.Log
import com.omnicontrol.agent.BuildConfig
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.network.auth.AuthApiService
import com.omnicontrol.agent.network.auth.LoginRequest
import com.omnicontrol.agent.network.auth.RefreshRequest

/**
 * Centralises the JWT authentication lifecycle for the device agent.
 *
 * Call [ensureAuthenticated] before opening the MQTT connection or making
 * authenticated REST requests. The method is idempotent: if a valid access
 * token is already present it returns immediately.
 */
class AuthManager(
    private val prefs: DevicePreferences,
    private val authApiService: AuthApiService
) {

    companion object {
        private const val TAG = "AuthManager"
        private const val ACCESS_TOKEN_LIFETIME_MS = 15 * 60 * 1000L   // 15 minutes
        private const val REFRESH_TOKEN_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
    }

    /**
     * Ensures a valid access token is stored in [DevicePreferences].
     * Returns true on success, false if authentication could not be established.
     */
    suspend fun ensureAuthenticated(): Boolean {
        // Fast path — token is present and still valid
        if (prefs.getAccessToken() != null && !prefs.isAccessTokenExpired()) {
            return true
        }
        // Try a silent refresh first
        if (prefs.getRefreshToken() != null && tryRefresh()) {
            return true
        }
        // Fall back to full login
        return tryLogin()
    }

    private suspend fun tryRefresh(): Boolean {
        val refreshToken = prefs.getRefreshToken() ?: return false
        return try {
            val response = authApiService.refresh(RefreshRequest(refreshToken))
            val newToken = response.takeIf { it.isSuccessful }?.body()?.data?.accessToken
            if (newToken != null) {
                prefs.saveAccessToken(newToken, ACCESS_TOKEN_LIFETIME_MS)
                true
            } else {
                Log.w(TAG, "Token refresh failed: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh exception: ${e.message}")
            false
        }
    }

    private suspend fun tryLogin(): Boolean {
        return try {
            val response = authApiService.login(
                LoginRequest(
                    username = BuildConfig.DEVICE_API_USERNAME,
                    password = BuildConfig.DEVICE_API_PASSWORD
                )
            )
            val body = response.takeIf { it.isSuccessful }?.body()?.data
            if (body != null) {
                prefs.saveTokens(body.accessToken, body.refreshToken ?: "", ACCESS_TOKEN_LIFETIME_MS)
                true
            } else {
                Log.e(TAG, "Login failed: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}")
            false
        }
    }
}
