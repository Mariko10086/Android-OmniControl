package com.omnicontrol.agent.system

import android.util.Log
import com.levine.as.utils.XShell
import java.io.File

/**
 * 通过设备厂商提供的 XShell（/system/xbin/xshell）或降级 su
 * 执行 pm install 静默安装 APK。
 *
 * XShell 优先级：
 *   /system/xbin/xshell 存在 → xshell 0（厂商特权 shell，无弹框）
 *   否则                     → su（标准 Root）
 */
class SystemInstaller {

    companion object {
        private const val TAG = "SystemInstaller"
    }

    /**
     * 静默安装 APK，返回 true 表示安装成功。
     */
    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK not found: ${apkFile.absolutePath}")
            return false
        }

        // 确保文件可被 pm 读取
        XShell.execCommand("chmod 644 ${apkFile.absolutePath}", true, false)

        val cmd = "pm install -r -d \"${apkFile.absolutePath}\""
        val result = XShell.execCommand(cmd, true, true)

        return if (result.result == 0) {
            Log.i(TAG, "Install success: ${apkFile.name} | ${result.successMsg?.trim()}")
            true
        } else {
            Log.e(TAG, "Install failed [${result.result}] | stderr: ${result.errorMsg?.trim()}")
            false
        }
    }
}
