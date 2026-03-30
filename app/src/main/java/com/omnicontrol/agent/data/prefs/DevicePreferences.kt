package com.omnicontrol.agent.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Manages the stable device identity, report sequence counter, and JWT token storage.
 *
 * Device ID is generated once on first launch using a random UUID and persisted in plain prefs.
 * JWT tokens are stored in EncryptedSharedPreferences (AES256-GCM) to protect credentials at rest.
 */
class DevicePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val PREFS_NAME           = "device_prefs"
        private const val ENCRYPTED_PREFS_NAME = "omnicontrol_secure"

        private const val KEY_DEVICE_ID        = "device_id"
        private const val KEY_REPORT_SEQUENCE  = "report_sequence"
        private const val KEY_IS_REGISTERED    = "device_registered"

        private const val KEY_ACCESS_TOKEN        = "jwt_access_token"
        private const val KEY_REFRESH_TOKEN       = "jwt_refresh_token"
        private const val KEY_ACCESS_EXPIRY_MS    = "jwt_access_expiry_ms"

        private const val KEY_ELEVATION_ATTEMPTED = "elevation_attempted"

        /** Safety buffer subtracted from nominal token lifetime before marking as expired */
        private const val TOKEN_EXPIRY_BUFFER_MS = 60_000L  // 1 minute
    }

    // ── Device identity ──────────────────────────────────────────────────────

    fun getOrCreateDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
    }

    fun nextReportSequence(): Long {
        val next = prefs.getLong(KEY_REPORT_SEQUENCE, 0L) + 1L
        prefs.edit().putLong(KEY_REPORT_SEQUENCE, next).apply()
        return next
    }

    // ── Registration flag ────────────────────────────────────────────────────

    fun markRegistered() {
        prefs.edit().putBoolean(KEY_IS_REGISTERED, true).apply()
    }

    fun isRegistered(): Boolean = prefs.getBoolean(KEY_IS_REGISTERED, false)

    // ── System elevation flag ─────────────────────────────────────────────────

    fun isElevationAttempted(): Boolean = prefs.getBoolean(KEY_ELEVATION_ATTEMPTED, false)
    fun markElevationAttempted() = prefs.edit().putBoolean(KEY_ELEVATION_ATTEMPTED, true).apply()

    // ── JWT token storage ────────────────────────────────────────────────────

    /**
     * Persists both tokens and records when the access token expires.
     * [accessTokenLifetimeMs] should be the token lifetime in milliseconds
     * (backend issues 15-minute tokens → 15 * 60 * 1000L).
     */
    fun saveTokens(accessToken: String, refreshToken: String, accessTokenLifetimeMs: Long) {
        val expiryMs = System.currentTimeMillis() + accessTokenLifetimeMs - TOKEN_EXPIRY_BUFFER_MS
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_ACCESS_EXPIRY_MS, expiryMs)
            .apply()
    }

    /** Updates only the access token, keeping the existing refresh token unchanged. */
    fun saveAccessToken(accessToken: String, accessTokenLifetimeMs: Long) {
        val expiryMs = System.currentTimeMillis() + accessTokenLifetimeMs - TOKEN_EXPIRY_BUFFER_MS
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_ACCESS_EXPIRY_MS, expiryMs)
            .apply()
    }

    fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)

    fun isAccessTokenExpired(): Boolean {
        val expiryMs = encryptedPrefs.getLong(KEY_ACCESS_EXPIRY_MS, 0L)
        return System.currentTimeMillis() >= expiryMs
    }

    fun clearTokens() {
        encryptedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCESS_EXPIRY_MS)
            .apply()
    }
}
