package com.omnicontrol.agent

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.omnicontrol.agent.mqtt.MqttManagerHolder
import com.omnicontrol.agent.mqtt.MqttService
import com.omnicontrol.agent.worker.WorkerScheduler

class OmniControlApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        MqttManagerHolder.init(this)
        MqttService.start(this)
        WorkerScheduler.scheduleAll(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR
            )
            .build()
}
