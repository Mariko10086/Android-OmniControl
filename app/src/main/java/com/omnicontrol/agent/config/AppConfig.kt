package com.omnicontrol.agent.config

import android.content.Context
import com.google.gson.Gson
import com.omnicontrol.agent.BuildConfig

/**
 * Central configuration for the OmniControl agent.
 *
 * Server URL, target packages, and report interval can be updated at runtime
 * by writing to SharedPreferences (e.g., via server-pushed config in the
 * report response). All reads fall back to BuildConfig compile-time defaults.
 */
object AppConfig {

    private const val PREFS_NAME = "omnicontrol_config"
    private const val KEY_SERVER_URL = "server_base_url"
    private const val KEY_TARGET_PACKAGES = "target_packages_json"
    private const val KEY_REPORT_INTERVAL_MINUTES = "report_interval_minutes"

    val DEFAULT_SERVER_URL: String = BuildConfig.SERVER_BASE_URL
    const val DEFAULT_REPORT_INTERVAL_MINUTES = 15L
    const val HEARTBEAT_TIMEOUT_SECONDS = 30L

    /**
     * Package names to monitor for installation status.
     * Replace with actual package names for your fleet.
     */
    val DEFAULT_TARGET_PACKAGES: List<String> = listOf(
        "com.example.requiredapp1",
        "com.example.requiredapp2",
        "com.android.chrome"
    )

    fun getServerUrl(context: Context): String {
        return prefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
            ?: DEFAULT_SERVER_URL
    }

    fun getTargetPackages(context: Context): List<String> {
        val json = prefs(context).getString(KEY_TARGET_PACKAGES, null)
        return if (json != null) {
            Gson().fromJson(json, Array<String>::class.java).toList()
        } else {
            DEFAULT_TARGET_PACKAGES
        }
    }

    fun getReportIntervalMinutes(context: Context): Long {
        return prefs(context).getLong(KEY_REPORT_INTERVAL_MINUTES, DEFAULT_REPORT_INTERVAL_MINUTES)
    }

    fun updateServerUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun updateTargetPackages(context: Context, packages: List<String>) {
        val json = Gson().toJson(packages)
        prefs(context).edit().putString(KEY_TARGET_PACKAGES, json).apply()
    }

    fun updateReportInterval(context: Context, intervalMinutes: Long) {
        prefs(context).edit().putLong(KEY_REPORT_INTERVAL_MINUTES, intervalMinutes).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
