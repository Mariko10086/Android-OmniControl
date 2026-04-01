package com.omnicontrol.agent.shell

import com.levine.`as`.utils.XShell

/**
 * 统一的 Shell 命令执行入口，底层使用设备厂商提供的 XShell。
 */
object ShellExecutor {

    /**
     * 以 root（xshell/su）执行单条命令，忽略输出结果。
     */
    fun exec(command: String) {
        XShell.execCommand(command, true, false)
    }

    /**
     * 以 root 执行单条命令，返回 stdout 字符串；失败返回 null。
     */
    fun execWithResult(command: String): String? {
        val result = XShell.execCommand(command, true, true)
        return if (result.result == 0) result.successMsg else null
    }

    /**
     * 以 root 执行多条命令。
     */
    fun execBatch(commands: List<String>) {
        XShell.execCommand(commands, true, false)
    }
}
