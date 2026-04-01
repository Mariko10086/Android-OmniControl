package com.omnicontrol.agent.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件日志工具：将日志同时输出到 logcat 和本地文件。
 *
 * 写入策略（按优先级）：
 *  1. Android 10+：使用 context.getExternalFilesDir(null)（无需存储权限）
 *     路径：/sdcard/Android/data/com.omnicontrol.agent/files/omnicontrol.log
 *  2. Android 9 及以下：使用 /sdcard/omnicontrol/omnicontrol.log（需 WRITE_EXTERNAL_STORAGE）
 *  3. 以上均失败时回退到应用内部私有目录 files/omnicontrol.log
 *
 * 文件超过 5MB 时自动删除轮转。
 * init() 可安全地被多次调用（重新定位日志文件，例如权限授予后重初始化）。
 */
object FileLogger {

    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L  // 5 MB
    private const val LOG_FILE_NAME = "omnicontrol.log"
    private const val PUBLIC_DIR_NAME = "omnicontrol"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    /**
     * 初始化日志文件路径。
     * 可在权限授予后再次调用以升级到外部存储路径。
     */
    fun init(context: Context) {
        logFile = resolveLogFile(context)
        Log.i("FileLogger", "Log file path: ${logFile?.absolutePath}")
    }

    private fun resolveLogFile(context: Context): File {
        // Android 10+：getExternalFilesDir 无需任何存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                return tryCreateFile(dir, LOG_FILE_NAME)
                    ?: File(context.filesDir, LOG_FILE_NAME)
            }
        }

        // Android 9 及以下：尝试写 /sdcard/omnicontrol/
        val publicDir = File(Environment.getExternalStorageDirectory(), PUBLIC_DIR_NAME)
        tryCreateFile(publicDir, LOG_FILE_NAME)?.let { return it }

        // 最终回退：内部私有目录
        return File(context.filesDir, LOG_FILE_NAME)
    }

    private fun tryCreateFile(dir: File, name: String): File? {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            if (!file.exists()) file.createNewFile()
            if (file.canWrite()) file else null
        } catch (_: Exception) {
            null
        }
    }

    fun v(tag: String, msg: String) {
        Log.v(tag, msg)
        write("V", tag, msg)
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
