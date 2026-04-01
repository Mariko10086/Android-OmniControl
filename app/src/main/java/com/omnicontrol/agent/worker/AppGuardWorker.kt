package com.omnicontrol.agent.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnicontrol.agent.config.AppConfig
import com.omnicontrol.agent.shell.ShellExecutor

/**
 * 每 5 分钟执行一次的守护 Worker：
 *  1. 对 [AppConfig.getTargetPackages] 中的每个包：
 *     a. pm grant — 授予运行时权限
 *     b. 检查进程是否存活，若未运行则通过 monkey 拉起
 *  2. 全程通过 ShellExecutor (su) 执行，无需用户交互
 */
class AppGuardWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "AppGuardWorker"
        private const val TAG = "AppGuardWorker"

        /** 需要强制授予的运行时权限 */
        private val RUNTIME_PERMISSIONS = listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
    }

    override suspend fun doWork(): Result {
        val packages = AppConfig.getTargetPackages(applicationContext)
        if (packages.isEmpty()) {
            Log.d(TAG, "No target packages configured, skip.")
            return Result.success()
        }

        var allOk = true
        for (pkg in packages) {
            try {
                grantPermissions(pkg)
                ensureRunning(pkg)
            } catch (e: Exception) {
                Log.e(TAG, "Guard failed for $pkg: ${e.message}")
                allOk = false
            }
        }

        return if (allOk) Result.success() else Result.retry()
    }

    // ── 权限授予 ──────────────────────────────────────────────────────────

    private fun grantPermissions(pkg: String) {
        for (permission in RUNTIME_PERMISSIONS) {
            val result = ShellExecutor.execWithResult("pm grant $pkg $permission")
            if (result != null) {
                Log.d(TAG, "Granted $permission → $pkg")
            } else {
                // 权限不存在或已授予时不视为致命错误
                Log.v(TAG, "Grant skipped [$pkg] $permission")
            }
        }
    }

    // ── 进程保活 ──────────────────────────────────────────────────────────

    private fun ensureRunning(pkg: String) {
        if (isProcessAlive(pkg)) {
            Log.d(TAG, "$pkg is running, skip launch.")
            return
        }

        Log.i(TAG, "$pkg not running, launching via monkey...")
        val result = ShellExecutor.execWithResult(
            "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
        )
        if (result != null) {
            Log.i(TAG, "Launched $pkg successfully.")
        } else {
            Log.w(TAG, "Launch failed for $pkg")
        }
    }

    /**
     * 通过 pidof 或 ps 检查目标包的进程是否存活。
     */
    private fun isProcessAlive(pkg: String): Boolean {
        // 优先用 pidof（更快）
        val pidof = ShellExecutor.execWithResult("pidof $pkg")
        if (!pidof.isNullOrBlank()) return true
        // 降级用 ps
        val ps = ShellExecutor.execWithResult("ps -A | grep $pkg")
        return !ps.isNullOrBlank()
    }
}
