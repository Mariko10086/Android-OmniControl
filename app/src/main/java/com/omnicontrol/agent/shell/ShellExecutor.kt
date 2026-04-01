package com.omnicontrol.agent.shell

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * 通过 xshell / su / sh 执行 shell 命令的统一入口。
 *
 * Shell 选择链（isRoot=true）：
 *   1. /system/xbin/xshell（厂商特供高权限 shell）
 *   2. su（标准 root shell）
 *   3. sh（无 root 降级，命令仍会执行但可能权限不足）
 *
 * 任意环节失败均返回 Result(exitCode=-1)，不抛异常，不打印完整堆栈，
 * 仅在第一次检测到无 root 环境时打印一次 warning。
 */
object ShellExecutor {

    private const val TAG = "ShellExecutor"

    private val XSHELL_FILE = File("/system/xbin/xshell")

    /** 是否已经打印过"无 root"警告（每次进程生命周期只打一次） */
    @Volatile
    private var rootUnavailableWarned = false

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val success get() = exitCode == 0
    }

    // ── 公共 API ──────────────────────────────────────────────────────────────

    /** 以 root 执行单条命令，忽略输出。失败不抛异常。 */
    fun exec(command: String) {
        runCatching { execInternal(command, useRoot = true) }
            .onFailure { e ->
                Log.w(TAG, "exec failed: $command — ${e.message}")
            }
    }

    /** 以 root 执行单条命令，返回 stdout；失败返回 null。 */
    fun execWithResult(command: String): String? {
        return runCatching { execInternal(command, useRoot = true) }
            .getOrNull()
            ?.takeIf { it.success }
            ?.stdout
            ?.ifBlank { null }
    }

    /** 以 root 批量执行命令。 */
    fun execBatch(commands: List<String>) {
        commands.forEach { exec(it) }
    }

    // ── 内部实现 ──────────────────────────────────────────────────────────────

    private fun execInternal(command: String, useRoot: Boolean): Result {
        val shell = resolveShell(useRoot)
        val proc = try {
            ProcessBuilder(shell)
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            // shell 本体不存在（e.g. su not found），降级到 sh
            if (useRoot && !rootUnavailableWarned) {
                rootUnavailableWarned = true
                Log.w(TAG, "Root shell '$shell' unavailable, falling back to sh. " +
                        "Root-only commands may not work on this device.")
            }
            ProcessBuilder("sh")
                .redirectErrorStream(false)
                .start()
        }

        return try {
            DataOutputStream(proc.outputStream).use { os ->
                os.writeBytes(command)
                os.writeBytes("\nexit\n")
                os.flush()
            }
            val stdout = BufferedReader(InputStreamReader(proc.inputStream)).readText().trim()
            val stderr = BufferedReader(InputStreamReader(proc.errorStream)).readText().trim()
            val exit = proc.waitFor()
            if (exit != 0) {
                Log.d(TAG, "cmd='$command' exit=$exit stderr=${stderr.take(200)}")
            }
            Result(exit, stdout, stderr)
        } catch (e: Exception) {
            proc.destroy()
            Result(-1, "", e.message ?: "")
        }
    }

    /**
     * 按优先级选择可用的 shell 可执行文件路径。
     * isRoot=true：xshell > su > sh
     * isRoot=false：sh
     */
    private fun resolveShell(useRoot: Boolean): String {
        if (!useRoot) return "sh"
        return when {
            XSHELL_FILE.exists() -> XSHELL_FILE.absolutePath
            else -> "su"   // 若 su 也不存在，execInternal 会捕获并降级 sh
        }
    }
}
