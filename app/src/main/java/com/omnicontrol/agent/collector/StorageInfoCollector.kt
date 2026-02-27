package com.omnicontrol.agent.collector

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.omnicontrol.agent.data.model.StorageInfo
import com.omnicontrol.agent.data.model.StorageVolume
import java.io.File

class StorageInfoCollector(private val context: Context) {

    /**
     * Collects internal storage stats via StatFs and external SD card if present.
     * No special permissions are required.
     */
    fun collect(): StorageInfo {
        return StorageInfo(
            internal = collectVolume(Environment.getDataDirectory()),
            externalSd = collectExternalSd()
        )
    }

    private fun collectVolume(path: File): StorageVolume {
        val stat = StatFs(path.absolutePath)
        val total = stat.totalBytes
        val available = stat.availableBytes
        val used = total - available
        val usedPercent = if (total > 0) (used.toFloat() / total.toFloat()) * 100f else 0f
        return StorageVolume(
            totalBytes = total,
            availableBytes = available,
            usedBytes = used,
            usedPercent = usedPercent
        )
    }

    private fun collectExternalSd(): StorageVolume? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+: index 0 is primary external (often internal flash),
            // index 1+ are true SD card mounts
            val dirs = context.getExternalFilesDirs(null)
            val sdCardDir = dirs.getOrNull(1) ?: return null
            if (!sdCardDir.exists()) return null
            // Walk up 4 levels from files/<pkg>/Android/: files → <pkg> → data → Android → mount root
            val mountRoot = sdCardDir
                .parentFile?.parentFile?.parentFile?.parentFile
                ?: return null
            try { collectVolume(mountRoot) } catch (e: Exception) { null }
        } else {
            @Suppress("DEPRECATION")
            val legacyExt = Environment.getExternalStorageDirectory()
            val state = Environment.getExternalStorageState()
            if (state == Environment.MEDIA_MOUNTED &&
                legacyExt != null &&
                legacyExt.absolutePath != Environment.getDataDirectory().absolutePath
            ) {
                try { collectVolume(legacyExt) } catch (e: Exception) { null }
            } else {
                null
            }
        }
    }
}
