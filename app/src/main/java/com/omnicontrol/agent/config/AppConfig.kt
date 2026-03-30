package com.omnicontrol.agent.config

import android.content.Context
import com.google.gson.Gson
import com.omnicontrol.agent.BuildConfig

/**
 * Central runtime configuration for the OmniControl agent.
 *
 * Server URL, MQTT broker, target packages and report interval can be updated at runtime
 * by writing to SharedPreferences. All reads fall back to BuildConfig compile-time defaults.
 */
object AppConfig {

    private const val PREFS_NAME                  = "omnicontrol_config"
    private const val KEY_SERVER_URL              = "server_base_url"
    private const val KEY_TARGET_PACKAGES         = "target_packages_json"
    private const val KEY_REPORT_INTERVAL_MINUTES = "report_interval_minutes"
    private const val KEY_MQTT_BROKER_HOST        = "mqtt_broker_host"
    private const val KEY_MQTT_BROKER_PORT        = "mqtt_broker_port"

    val DEFAULT_SERVER_URL: String       get() = BuildConfig.SERVER_BASE_URL
    val DEFAULT_MQTT_BROKER_HOST: String get() = BuildConfig.MQTT_BROKER_HOST
    val DEFAULT_MQTT_BROKER_PORT: Int    get() = BuildConfig.MQTT_BROKER_PORT.toInt()
    const val DEFAULT_REPORT_INTERVAL_MINUTES     = 15L
    const val HTTP_TIMEOUT_SECONDS                = 30L

    val DEFAULT_TARGET_PACKAGES: List<String> = listOf(
        "com.sqh.market",
        "com.magicalbox.devicegather",
        "com.android.webview"
    )

    // ── Server URL (REST) ────────────────────────────────────────────────────

    fun getServerUrl(context: Context): String =
        prefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    fun updateServerUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    // ── MQTT broker ──────────────────────────────────────────────────────────

    fun getMqttBrokerHost(context: Context): String =
        prefs(context).getString(KEY_MQTT_BROKER_HOST, DEFAULT_MQTT_BROKER_HOST)
            ?: DEFAULT_MQTT_BROKER_HOST

    fun getMqttBrokerPort(context: Context): Int =
        prefs(context).getInt(KEY_MQTT_BROKER_PORT, DEFAULT_MQTT_BROKER_PORT)

    fun updateMqttBroker(context: Context, host: String, port: Int) {
        prefs(context).edit()
            .putString(KEY_MQTT_BROKER_HOST, host)
            .putInt(KEY_MQTT_BROKER_PORT, port)
            .apply()
    }

    // ── Target packages ──────────────────────────────────────────────────────

    fun getTargetPackages(context: Context): List<String> {
        val json = prefs(context).getString(KEY_TARGET_PACKAGES, null)
        return if (json != null) {
            Gson().fromJson(json, Array<String>::class.java).toList()
        } else {
            DEFAULT_TARGET_PACKAGES
        }
    }

    fun updateTargetPackages(context: Context, packages: List<String>) {
        val json = Gson().toJson(packages)
        prefs(context).edit().putString(KEY_TARGET_PACKAGES, json).apply()
    }

    // ── Report interval ──────────────────────────────────────────────────────

    fun getReportIntervalMinutes(context: Context): Long =
        prefs(context).getLong(KEY_REPORT_INTERVAL_MINUTES, DEFAULT_REPORT_INTERVAL_MINUTES)

    fun updateReportInterval(context: Context, intervalMinutes: Long) {
        prefs(context).edit().putLong(KEY_REPORT_INTERVAL_MINUTES, intervalMinutes).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
