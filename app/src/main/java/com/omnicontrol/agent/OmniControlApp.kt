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
        // 先用内部目录初始化日志，确保 crash handler 等最早期日志有地方写
        FileLogger.init(this)
        installCrashHandler()

        // 权限授予完成后重新初始化日志，升级到外部存储路径
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            grantSelfPermissions()
            // 权限授完后重新 init，尝试写到 getExternalFilesDir / /sdcard/
            FileLogger.init(applicationContext)
            FileLogger.i("OmniControlApp", "FileLogger re-initialized after permission grant")
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
