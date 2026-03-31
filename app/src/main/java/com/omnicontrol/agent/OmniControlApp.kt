package com.omnicontrol.agent

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.work.Configuration
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.mqtt.MqttManagerHolder
import com.omnicontrol.agent.mqtt.MqttService
import com.omnicontrol.agent.shell.ShellExecutor
import com.omnicontrol.agent.system.ProcessGuard
import com.omnicontrol.agent.system.ScreenKeepAwake
import com.omnicontrol.agent.system.SelfElevation
import com.omnicontrol.agent.util.FileLogger
import com.omnicontrol.agent.worker.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OmniControlApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        installCrashHandler()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            grantSelfPermissions()
        }
        checkAndElevateIfNeeded()
        MqttManagerHolder.init(this)
        MqttService.start(this)
        WorkerScheduler.scheduleAll(this)

        // Apply screen keep-awake settings on every app start (not just when Activity opens)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ScreenKeepAwake.applySystemSettings()
        }

        // Protect this process from OOM kill and start service watchdog
        val guardScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        guardScope.launch { ProcessGuard.protectCurrentProcess() }
        ProcessGuard.startWatchdog(this, guardScope)
    }

    private suspend fun grantSelfPermissions() {
        val perms = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions ?: return
        } catch (e: Exception) {
            FileLogger.e("OmniControlApp", "grantSelfPermissions: cannot read permissions: ${e.message}")
            return
        }
        FileLogger.i("OmniControlApp", "Granting ${perms.size} permissions to self via root shell")
        for (perm in perms) {
            ShellExecutor.exec("pm grant $packageName $perm")
        }
        FileLogger.i("OmniControlApp", "Self permission grant complete")
    }

    private fun checkAndElevateIfNeeded() {
        if (SelfElevation.isAlreadySystemApp(this)) {
            FileLogger.i("OmniControlApp", "Running as system app")
            return
        }
        val prefs = DevicePreferences(this)
        if (prefs.isElevationAttempted()) {
            FileLogger.w("OmniControlApp", "Elevation already attempted, skipping")
            return
        }
        prefs.markElevationAttempted()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            FileLogger.i("OmniControlApp", "Not a system app — attempting self elevation")
            val success = SelfElevation.elevate(this@OmniControlApp)
            if (!success) {
                FileLogger.e("OmniControlApp", "Self elevation failed, continuing as regular app")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR
            )
            .build()

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            FileLogger.wtf("CRASH", "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
