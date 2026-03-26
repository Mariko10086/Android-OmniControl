package com.omnicontrol.agent.mqtt

import android.content.Context

/**
 * Application-scoped singleton that holds the shared [MqttManager] instance.
 *
 * WorkManager workers run in isolated coroutine scopes and cannot hold a direct reference
 * to [MqttManager]. They call [get] to retrieve the shared instance initialised by
 * [com.omnicontrol.agent.OmniControlApp] at startup.
 */
object MqttManagerHolder {

    @Volatile
    private var instance: MqttManager? = null

    fun init(context: Context): MqttManager {
        return instance ?: synchronized(this) {
            instance ?: MqttManager(context.applicationContext).also { instance = it }
        }
    }

    fun get(context: Context): MqttManager = instance ?: init(context)
}
