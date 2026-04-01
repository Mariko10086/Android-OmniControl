package com.omnicontrol.agent.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件日志工具：将日志同时输出到 logcat 和本地文件（files/omnicontrol.log）。
 * 文件超过 5MB 时自动轮转。
 */
object FileLogger {

    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L  // 5 MB
    private const val LOG_FILE_NAME = "omnicontrol.log"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        write("I", tag, msg)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        write("D", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        write("W", tag, msg)
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        write("E", tag, msg)
    }

    fun wtf(tag: String, msg: String, throwable: Throwable? = null) {
        Log.wtf(tag, msg, throwable)
        write("WTF", tag, "$msg\n${throwable?.stackTraceToString() ?: ""}")
    }

    private fun write(level: String, tag: String, msg: String) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() > MAX_FILE_SIZE) {
                file.delete()
            }
            FileWriter(file, true).use { writer ->
                writer.appendLine("${dateFormat.format(Date())} $level/$tag: $msg")
            }
        } catch (_: Exception) {
            // 日志写入失败不影响主流程
        }
    }
}
