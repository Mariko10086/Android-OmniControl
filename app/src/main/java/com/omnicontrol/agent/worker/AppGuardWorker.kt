package com.omnicontrol.agent.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.levine.as.utils.XShell
import com.omnicontrol.agent.config.AppConfig

/**
 * 每 5 分钟执行一次的守护 Worker：
 *  1. 对 [AppConfig.getTargetPackages] 中的每个包：
 *     a. pm grant — 授予运行时权限
 *     b. 检查进程是否存活，若未运行则通过 monkey 拉起
 *  2. 全程通过 XShell（xshell/su）执行，无需用户交互
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
            val result = XShell.execCommand("pm grant $pkg $permission", true, false)
            if (result.result == 0) {
                Log.d(TAG, "Granted $permission → $pkg")
            } else {
                // 权限不存在或已授予时 pm 也可能返回非 0，不视为致命错误
                Log.v(TAG, "Grant skipped [$pkg] $permission (code=${result.result})")
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
        val result = XShell.execCommand(
            "monkey -p $pkg -c android.intent.category.LAUNCHER 1",
            true,
            true
        )

        if (result.result == 0) {
            Log.i(TAG, "Launched $pkg successfully.")
        } else {
            Log.w(TAG, "Launch failed for $pkg [${result.result}]: ${result.errorMsg?.trim()}")
        }
    }

    /**
     * 通过 pidof 或 ps 检查目标包的进程是否存活。
     */
    private fun isProcessAlive(pkg: String): Boolean {
        // 优先用 pidof（更快）
        val pidofResult = XShell.execCommand("pidof $pkg", true, true)
        if (pidofResult.result == 0 && !pidofResult.successMsg.isNullOrBlank()) {
            return true
        }
        // 降级用 ps
        val psResult = XShell.execCommand("ps -A | grep $pkg", true, true)
        return psResult.result == 0 && !psResult.successMsg.isNullOrBlank()
    }
}
