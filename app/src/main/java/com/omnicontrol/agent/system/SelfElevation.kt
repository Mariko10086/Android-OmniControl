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
 *   1. 首次启动且不是系统 APP → 尝试 elevate()
 *   2. elevate() 做三件事：
 *      a. 复制 APK 到 /system/priv-app/<pkg>/
 *      b. 写入特权权限白名单 /system/etc/permissions/privapp-permissions-<pkg>.xml
 *         （Android 8+ 必须，否则 INSTALL_PACKAGES 等特权权限不会自动授予）
 *      c. reboot 使系统重新扫描 priv-app 目录
 *   3. reboot 后 APP 以系统身份启动 → isAlreadySystemApp()=true，直接跳过
 *   4. 若 elevate 失败 → APP 更新安装后（MY_PACKAGE_REPLACED）标记自动清除，允许重试
 */
object SelfElevation {

    private const val TAG = "SelfElevation"

    /**
     * Android 8+ 特权权限白名单内容。
     * 只需列出 signatureOrSystem / privileged 等级的权限，
     * INTERNET / ACCESS_NETWORK_STATE 等普通权限不需要列入。
     */
    private fun buildPrivappPermissionsXml(pkgName: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <permissions>
            <privapp-permissions package="$pkgName">
                <permission name="android.permission.INSTALL_PACKAGES"/>
                <permission name="android.permission.DELETE_PACKAGES"/>
                <permission name="android.permission.READ_PHONE_STATE"/>
                <permission name="android.permission.REBOOT"/>
                <permission name="android.permission.WRITE_SETTINGS"/>
                <permission name="android.permission.DISABLE_KEYGUARD"/>
                <permission name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
                <permission name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
            </privapp-permissions>
        </permissions>
    """.trimIndent()

    fun isAlreadySystemApp(context: Context): Boolean {
        val flags = context.applicationInfo.flags
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /**
     * 清除 elevation_attempted 标记，允许下次启动重新尝试提权。
     * 在 APP 更新安装后（MY_PACKAGE_REPLACED）由 BootReceiver 调用。
     */
    fun resetElevationFlag(context: Context) {
        context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            .edit().remove("elevation_attempted").apply()
        FileLogger.i(TAG, "elevation_attempted flag cleared (app updated)")
    }

    /**
     * 将 APK 复制到 /system/priv-app，写入特权权限白名单，然后 reboot。
     * @return true 表示所有命令下发成功，设备将重启后生效；false 表示中途失败
     */
    suspend fun elevate(context: Context): Boolean {
        return try {
            val apkPath = context.applicationInfo.sourceDir
            val pkgName = context.packageName
            val destDir = "/system/priv-app/$pkgName"
            val permXmlPath = "/system/etc/permissions/privapp-permissions-$pkgName.xml"

            FileLogger.i(TAG, "Attempting self elevation: $apkPath → $destDir")

            // ── Step 1: 挂载 /system 可写 ──────────────────────────────────
            val mountResult = ShellExecutor.execWithResult("mount -o remount,rw /system")
            if (mountResult == null) {
                FileLogger.w(TAG, "mount remount,rw returned non-zero — attempting anyway (some ROMs are writable by default)")
            }

            // ── Step 2: 复制 APK ────────────────────────────────────────────
            ShellExecutor.exec("mkdir -p $destDir")
            ShellExecutor.exec("cp $apkPath $destDir/$pkgName.apk")
            ShellExecutor.exec("chmod 644 $destDir/$pkgName.apk")
            ShellExecutor.exec("chown root:root $destDir/$pkgName.apk")

            // 验证 APK 是否复制成功
            val apkVerify = ShellExecutor.execWithResult("ls $destDir/$pkgName.apk")
            if (apkVerify == null) {
                FileLogger.e(TAG, "APK copy failed: $destDir/$pkgName.apk not found")
                ShellExecutor.exec("mount -o remount,ro /system")
                return false
            }
            FileLogger.i(TAG, "APK copied to priv-app: $destDir/$pkgName.apk")

            // ── Step 3: 写入特权权限白名单（Android 8+ 必须）──────────────
            val xmlContent = buildPrivappPermissionsXml(pkgName)
            // 用 echo 写入（内容不含单引号，安全）
            // 多行用 printf 更可靠
            val writeXmlCmd = "printf '%s' '${xmlContent.replace("'", "\\'")}' > $permXmlPath"
            ShellExecutor.exec(writeXmlCmd)
            ShellExecutor.exec("chmod 644 $permXmlPath")
            ShellExecutor.exec("chown root:root $permXmlPath")

            val xmlVerify = ShellExecutor.execWithResult("ls $permXmlPath")
            if (xmlVerify == null) {
                FileLogger.w(TAG, "privapp-permissions XML write may have failed: $permXmlPath — will proceed anyway")
            } else {
                FileLogger.i(TAG, "privapp-permissions XML written: $permXmlPath")
            }

            // ── Step 4: 挂回只读，reboot ────────────────────────────────────
            ShellExecutor.exec("mount -o remount,ro /system")
            FileLogger.i(TAG, "Self elevation complete, rebooting to apply...")
            ShellExecutor.exec("reboot")
            true
        } catch (e: Exception) {
            FileLogger.e(TAG, "Self elevation exception: ${e.message}")
            false
        }
    }
}
