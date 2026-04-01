package com.omnicontrol.agent.system

import android.util.Log
import com.omnicontrol.agent.shell.ShellExecutor
import java.io.File

/**
 * 通过 root shell（su）执行 pm install 静默安装 APK。
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
        ShellExecutor.exec("chmod 644 ${apkFile.absolutePath}")

        val cmd = "pm install -r -d \"${apkFile.absolutePath}\""
        val result = ShellExecutor.execWithResult(cmd)

        return if (result != null) {
            Log.i(TAG, "Install success: ${apkFile.name} | $result")
            true
        } else {
            Log.e(TAG, "Install failed: ${apkFile.name}")
            false
        }
    }
}
