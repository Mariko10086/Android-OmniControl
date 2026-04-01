package com.omnicontrol.agent.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnicontrol.agent.config.AppConfig
import com.omnicontrol.agent.shell.ShellExecutor
import com.omnicontrol.agent.util.FileLogger

/**
 * 每 5 分钟执行一次的守护 Worker：
 *  1. 对 [AppConfig.getTargetPackages] 中的每个包：
 *     a. pm grant — 授予运行时权限（仅授予包已声明的权限）
 *     b. 检查进程是否存活，若未运行则尝试拉起
 *  2. 全程通过 ShellExecutor (su) 执行，无需用户交互
 *
 * 启动策略（依次降级）：
 *  1. 通过 `cmd package resolve-activity` 解析 LAUNCHER Activity，再 `am start -n` 精确启动
 *  2. `am start -a MAIN -c LAUNCHER -p <pkg>`（Android 5+ 宽松匹配）
 *  3. `monkey -p <pkg> 1`（无 category 过滤，兼容无 LAUNCHER Activity 的后台包）
 *  4. 以上均失败则跳过（记录 warn，不影响整体 Worker 结果）
 */
class AppGuardWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "AppGuardWorker"
        private const val TAG = "AppGuardWorker"

        /** 需要尝试授予的运行时权限 */
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
            FileLogger.d(TAG, "No target packages configured, skip.")
            return Result.success()
        }

        var allOk = true
        for (pkg in packages) {
            try {
                grantPermissions(pkg)
                ensureRunning(pkg)
            } catch (e: Exception) {
                FileLogger.e(TAG, "Guard failed for $pkg: ${e.message}")
                allOk = false
            }
        }

        return if (allOk) Result.success() else Result.retry()
    }

    // ── 权限授予 ──────────────────────────────────────────────────────────

    /**
     * 仅授予目标包已在 manifest 中声明的权限，跳过未声明的以避免 SecurityException 噪声。
     */
    private fun grantPermissions(pkg: String) {
        // 先查出该包已声明的权限集合
        val declaredPerms = getDeclaredPermissions(pkg)
        if (declaredPerms.isEmpty()) {
            FileLogger.d(TAG, "[$pkg] no declared permissions, skip grant.")
            return
        }

        for (permission in RUNTIME_PERMISSIONS) {
            if (!declaredPerms.contains(permission)) continue   // 未声明，直接跳过，不打 warn
            val result = ShellExecutor.execWithResult("pm grant $pkg $permission")
            if (result != null) {
                FileLogger.d(TAG, "Granted $permission → $pkg")
            } else {
                FileLogger.v(TAG, "Grant skipped [$pkg] $permission")
            }
        }
    }

    /**
     * 通过 `pm dump <pkg>` 解析包已请求的权限列表。
     * 返回空集合表示查询失败或包未安装。
     */
    private fun getDeclaredPermissions(pkg: String): Set<String> {
        val dump = ShellExecutor.execWithResult("pm dump $pkg") ?: return emptySet()
        // pm dump 输出中 "requested permissions:" 块列出所有声明的权限
        val result = mutableSetOf<String>()
        var inSection = false
        for (line in dump.lines()) {
            val trimmed = line.trim()
            if (trimmed == "requested permissions:") {
                inSection = true
                continue
            }
            if (inSection) {
                if (trimmed.startsWith("android.permission.") || trimmed.startsWith("com.")) {
                    result.add(trimmed)
                } else {
                    // 遇到非权限行则退出该 section
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) break
                }
            }
        }
        return result
    }

    // ── 进程保活 ──────────────────────────────────────────────────────────

    private fun ensureRunning(pkg: String) {
        if (isProcessAlive(pkg)) {
            FileLogger.d(TAG, "$pkg is running, skip launch.")
            return
        }

        FileLogger.i(TAG, "$pkg not running, attempting launch...")
        val launched = launchApp(pkg)
        if (launched) {
            FileLogger.i(TAG, "$pkg launched successfully.")
        } else {
            FileLogger.w(TAG, "All launch methods failed for $pkg")
        }
    }

    /**
     * 依次尝试三种启动方式，任意一种成功即返回 true。
     *
     * 方式一：`cmd package resolve-activity` 解析 LAUNCHER Activity → `am start -n <component>`
     *   适用于有 LAUNCHER Activity 的普通应用，最精确。
     *
     * 方式二：`am start -a MAIN -c LAUNCHER -p <pkg>`
     *   Android 5+ 支持 `-p` 参数，系统自动匹配目标包的 MAIN/LAUNCHER Activity。
     *
     * 方式三：`monkey -p <pkg> 1`（不加 `-c LAUNCHER` 过滤）
     *   去掉 category 过滤后，monkey 会启动包内任意可测试的 Activity，
     *   对后台 daemon 类包（无 LAUNCHER）也有机会触发。
     */
    private fun launchApp(pkg: String): Boolean {
        // 方式一：精确解析 LAUNCHER Activity 后 am start
        val component = resolveLauncherActivity(pkg)
        if (!component.isNullOrBlank()) {
            FileLogger.d(TAG, "[$pkg] resolved component: $component, using am start -n")
            val r = ShellExecutor.execWithResult("am start -n $component")
            if (r != null) return true
            FileLogger.w(TAG, "[$pkg] am start -n $component failed, trying fallback")
        } else {
            FileLogger.d(TAG, "[$pkg] no LAUNCHER activity resolved, skipping method 1")
        }

        // 方式二：am start -p (Android 5+)
        val r2 = ShellExecutor.execWithResult(
            "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg"
        )
        if (r2 != null) return true
        FileLogger.d(TAG, "[$pkg] am start -p failed, trying monkey fallback")

        // 方式三：monkey 不加 -c 过滤（后台 daemon 的最后手段）
        val r3 = ShellExecutor.execWithResult("monkey -p $pkg 1")
        return r3 != null
    }

    /**
     * 使用 `cmd package resolve-activity` 解析包的 LAUNCHER Activity 组件名。
     * 返回形如 "com.example.app/.MainActivity" 的字符串，或 null（包未安装 / 无 LAUNCHER Activity）。
     */
    private fun resolveLauncherActivity(pkg: String): String? {
        val output = ShellExecutor.execWithResult(
            "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $pkg"
        ) ?: return null

        // 输出末行若包含 "/" 则为组件名，例如 "com.example/.MainActivity"
        return output.trim().lines()
            .lastOrNull { it.contains("/") }
            ?.trim()
    }

    /**
     * 通过 pidof 或 ps 检查目标包的进程是否存活。
     */
    private fun isProcessAlive(pkg: String): Boolean {
        val pidof = ShellExecutor.execWithResult("pidof $pkg")
        if (!pidof.isNullOrBlank()) return true
        val ps = ShellExecutor.execWithResult("ps -A | grep $pkg")
        return !ps.isNullOrBlank()
    }
}
