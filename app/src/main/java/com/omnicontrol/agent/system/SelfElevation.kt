package com.omnicontrol.agent.system

import android.content.Context
import android.content.pm.ApplicationInfo
import com.omnicontrol.agent.shell.ShellExecutor
import com.omnicontrol.agent.util.FileLogger

/**
 * 尝试将本 APP 提升为系统 APP（复制到 /system/priv-app）。
 * 仅在设备具备 root/xshell 权限时有效。
 *
 * 流程：
 *   1. 首次启动且不是系统 APP → 尝试 elevate()，写 elevation_attempted=true，reboot
 *   2. reboot 后 APP 以系统身份启动 → isAlreadySystemApp()=true，直接跳过
 *   3. 若 elevate 失败（/system mount 失败等）→ 标记写 attempted=true 但记录失败原因，
 *      下次 APP 升级安装后（MY_PACKAGE_REPLACED）标记会自动清除，允许重试
 */
object SelfElevation {

    private const val TAG = "SelfElevation"

    fun isAlreadySystemApp(context: Context): Boolean {
        val flags = context.applicationInfo.flags
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /**
     * 清除 elevation_attempted 标记，允许下次启动重新尝试提权。
     * 在 APP 更新安装后（MY_PACKAGE_REPLACED）由 BootReceiver 调用，
     * 确保新版本安装后若仍未成为系统 APP 会重新尝试。
     */
    fun resetElevationFlag(context: Context) {
        context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            .edit().remove("elevation_attempted").apply()
        FileLogger.i(TAG, "elevation_attempted flag cleared (app updated)")
    }

    /**
     * 将 APK 复制到 /system/priv-app 并设置权限，然后重启以生效。
     * @return true 表示命令下发成功（需重启后才真正生效）
     */
    suspend fun elevate(context: Context): Boolean {
        return try {
            val apkPath = context.applicationInfo.sourceDir
            val pkgName = context.packageName
            val destDir = "/system/priv-app/$pkgName"

            FileLogger.i(TAG, "Attempting self elevation: $apkPath → $destDir")

            // 挂载 /system 为可写
            val mountResult = ShellExecutor.execWithResult("mount -o remount,rw /system")
            if (mountResult == null) {
                // mount 失败时尝试继续（部分厂商 /system 默认可写）
                FileLogger.w(TAG, "mount remount,rw failed — attempting copy anyway")
            }

            ShellExecutor.exec("mkdir -p $destDir")
            ShellExecutor.exec("cp $apkPath $destDir/$pkgName.apk")
            ShellExecutor.exec("chmod 644 $destDir/$pkgName.apk")
            ShellExecutor.exec("chown root:root $destDir/$pkgName.apk")
            ShellExecutor.exec("mount -o remount,ro /system")

            // 验证复制是否成功
            val verifyResult = ShellExecutor.execWithResult("ls $destDir/$pkgName.apk")
            if (verifyResult == null) {
                FileLogger.e(TAG, "Self elevation failed: APK not found in $destDir after copy")
                return false
            }

            FileLogger.i(TAG, "Self elevation succeeded, rebooting to apply...")
            ShellExecutor.exec("reboot")
            true
        } catch (e: Exception) {
            FileLogger.e(TAG, "Self elevation exception: ${e.message}")
            false
        }
    }
}
