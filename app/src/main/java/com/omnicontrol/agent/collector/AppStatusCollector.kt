package com.omnicontrol.agent.collector

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.omnicontrol.agent.data.model.AppStatusInfo

class AppStatusCollector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Checks each package in [packageNames] and returns a full status record.
     * Uses PackageManager.getPackageInfo() — no special permissions required.
     */
    fun collect(packageNames: List<String>): List<AppStatusInfo> {
        return packageNames.map { checkPackage(it) }
    }

    private fun checkPackage(packageName: String): AppStatusInfo {
        return try {
            val info = pm.getPackageInfo(packageName, 0)
            val appInfo = info.applicationInfo
            AppStatusInfo(
                packageName = packageName,
                installed = true,
                versionName = info.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                },
                enabled = appInfo?.enabled,
                firstInstallTimeMs = info.firstInstallTime,
                lastUpdateTimeMs = info.lastUpdateTime
            )
        } catch (e: PackageManager.NameNotFoundException) {
            AppStatusInfo(
                packageName = packageName,
                installed = false,
                versionName = null,
                versionCode = null,
                enabled = null,
                firstInstallTimeMs = null,
                lastUpdateTimeMs = null
            )
        }
    }
}
