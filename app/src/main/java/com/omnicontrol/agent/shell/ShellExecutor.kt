package com.omnicontrol.agent.shell

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 通过 su / sh 执行 shell 命令的统一入口。
 * 不依赖任何厂商系统库，可在所有编译环境正常构建。
 */
object ShellExecutor {

    private const val TAG = "ShellExecutor"

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val success get() = exitCode == 0
    }

    /**
     * 以 root（su）执行单条命令，忽略输出。
     */
    fun exec(command: String) {
        runCatching { execInternal(command, useRoot = true) }
            .onFailure { Log.e(TAG, "exec failed: $command", it) }
    }

    /**
     * 以 root 执行单条命令，返回 stdout；失败返回 null。
     */
    fun execWithResult(command: String): String? {
        return runCatching { execInternal(command, useRoot = true) }
            .getOrNull()
            ?.takeIf { it.success }
            ?.stdout
            ?.ifBlank { null }
    }

    /**
     * 以 root 执行多条命令。
     */
    fun execBatch(commands: List<String>) {
        commands.forEach { exec(it) }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun execInternal(command: String, useRoot: Boolean): Result {
        val shell = if (useRoot) "su" else "sh"
        val proc = ProcessBuilder(shell)
            .redirectErrorStream(false)
            .start()

        OutputStreamWriter(proc.outputStream).use { writer ->
            writer.write(command)
            writer.write("\nexit\n")
            writer.flush()
        }

        val stdout = BufferedReader(InputStreamReader(proc.inputStream)).readText().trim()
        val stderr = BufferedReader(InputStreamReader(proc.errorStream)).readText().trim()
        val exit = proc.waitFor()

        if (exit != 0) {
            Log.w(TAG, "cmd='$command' exit=$exit stderr=$stderr")
        }
        return Result(exit, stdout, stderr)
    }
}
