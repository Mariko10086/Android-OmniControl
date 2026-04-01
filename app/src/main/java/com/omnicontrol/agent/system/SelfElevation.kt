package com.omnicontrol.agent.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.omnicontrol.agent.shell.ShellExecutor

/**
 * 尝试将本 APP 提升为系统 APP（复制到 /system/priv-app）。
 * 仅在设备具备 root/xshell 权限时有效。
 */
object SelfElevation {

    private const val TAG = "SelfElevation"

    /**
     * 判断当前 APP 是否已经是系统 APP。
     */
    fun isAlreadySystemApp(context: Context): Boolean {
        val flags = context.applicationInfo.flags
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /**
     * 将 APK 复制到 /system/priv-app 并设置权限，然后重启以生效。
     * @return true 表示命令下发成功
     */
    suspend fun elevate(context: Context): Boolean {
        return try {
            val apkPath = context.applicationInfo.sourceDir
            val pkgName = context.packageName
            val destDir = "/system/priv-app/$pkgName"

            ShellExecutor.exec("mount -o remount,rw /system")
            ShellExecutor.exec("mkdir -p $destDir")
            ShellExecutor.exec("cp $apkPath $destDir/$pkgName.apk")
            ShellExecutor.exec("chmod 644 $destDir/$pkgName.apk")
            ShellExecutor.exec("chown root:root $destDir/$pkgName.apk")
            ShellExecutor.exec("mount -o remount,ro /system")
            Log.i(TAG, "Self elevation commands sent, rebooting...")
            ShellExecutor.exec("reboot")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Self elevation failed: ${e.message}")
            false
        }
    }
}
