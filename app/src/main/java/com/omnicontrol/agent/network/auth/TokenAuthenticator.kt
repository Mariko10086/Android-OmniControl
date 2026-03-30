package com.omnicontrol.agent.network.auth

import com.omnicontrol.agent.data.prefs.DevicePreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Handles 401 responses by attempting a token refresh before retrying the original request.
 *
 * Synchronised to prevent concurrent workers from triggering parallel refresh races.
 * If refresh fails (e.g. refresh token expired) the tokens are cleared and null is returned,
 * signalling OkHttp to propagate the 401 to the caller so [AuthManager] can re-login.
 */
class TokenAuthenticator(
    private val prefs: DevicePreferences,
    private val authApiService: AuthApiService
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // If we have already retried once, stop — avoid infinite loops
        if (responseCount(response) >= 2) return null

        val refreshToken = prefs.getRefreshToken() ?: run {
            prefs.clearTokens()
            return null
        }

        val refreshResult = runBlocking {
            try {
                authApiService.refresh(RefreshRequest(refreshToken))
            } catch (e: Exception) {
                null
            }
        }

        val newAccessToken = refreshResult
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?.data?.accessToken

        return if (newAccessToken != null) {
            // 15 minutes = 15 * 60 * 1000 ms
            prefs.saveAccessToken(newAccessToken, 15 * 60 * 1000L)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        } else {
            prefs.clearTokens()
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var r: Response? = response.priorResponse
        while (r != null) {
            count++
            r = r.priorResponse
        }
        return count
    }
}
