package com.omnicontrol.agent.system

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.omnicontrol.agent.shell.ShellExecutor

/**
 * 通过 root shell 持久化屏幕常亮 + 禁用锁屏设置。
 *
 * 普通 App 无法写入 Settings.System / Settings.Secure，需要 xshell/su 执行
 * `settings put` 命令绕过权限限制。
 */
object ScreenKeepAwake {

    private const val TAG = "ScreenKeepAwake"

    /** 目标屏幕超时：Int.MAX_VALUE 毫秒（约 24 天，等同于永不息屏）*/
    private const val TIMEOUT_NEVER = Int.MAX_VALUE

    /**
     * 通过 root shell 写入系统设置，实现永不息屏 + 禁用锁屏。
     * @return true 表示命令执行成功
     */
    suspend fun applySystemSettings(): Boolean {
        return try {
            ShellExecutor.exec("settings put system screen_off_timeout $TIMEOUT_NEVER")
            ShellExecutor.exec("settings put secure lockscreen.disabled 1")
            ShellExecutor.exec("settings put secure screensaver_enabled 0")
            Log.i(TAG, "Screen keep-awake settings applied")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply screen settings: ${e.message}")
            false
        }
    }

    /**
     * 读取当前屏幕常亮 / 锁屏状态（带 applied 参数，供 MainActivity 调用）。
     */
    fun queryCurrentStatus(context: Context, applied: Boolean = false): ScreenAwakeStatus {
        val timeoutMs = try {
            Settings.System.getLong(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                -1L
            )
        } catch (e: Exception) {
            -1L
        }

        val lockScreenDisabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                "lockscreen.disabled",
                0
            ) == 1
        } catch (e: Exception) {
            false
        }

        return ScreenAwakeStatus(
            screenTimeoutMs = timeoutMs,
            lockScreenDisabled = lockScreenDisabled
        )
    }

    /**
     * 无 applied 参数版本，供 DashboardViewModel 调用。
     */
    fun queryCurrentStatus(context: Context): ScreenAwakeStatus = queryCurrentStatus(context, false)
}
