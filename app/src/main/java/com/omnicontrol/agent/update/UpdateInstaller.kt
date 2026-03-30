package com.omnicontrol.agent.update

import android.content.Context
import android.os.Build
import android.util.Log
import com.omnicontrol.agent.data.model.mqtt.UpdateCommandPayload
import com.omnicontrol.agent.data.model.mqtt.UpdateResultPayload
import com.omnicontrol.agent.data.prefs.DevicePreferences
import com.omnicontrol.agent.system.SystemInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Handles the full APK update pipeline:
 * 1. Download the APK to the cache directory.
 * 2. Verify MD5 checksum.
 * 3. Silent install via pm install (requires root / system-app privileges).
 * 4. Return a [UpdateResultPayload] to be reported back to the server.
 */
class UpdateInstaller(private val context: Context) {

    companion object {
        private const val TAG = "UpdateInstaller"
        private const val DOWNLOAD_TIMEOUT_SECONDS = 120L
    }

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val systemInstaller = SystemInstaller()

    suspend fun install(command: UpdateCommandPayload): UpdateResultPayload {
        val deviceId = DevicePreferences(context).getOrCreateDeviceId()
        val apkFile = File(context.cacheDir, "update_${command.taskUuid}.apk")

        return try {
            // Step 1: Download
            downloadApk(command.apkUrl, apkFile)

            // Step 2: Verify MD5
            val actualMd5 = computeMd5(apkFile)
            if (!actualMd5.equals(command.apkMd5, ignoreCase = true)) {
                apkFile.delete()
                Log.e(TAG, "MD5 mismatch for ${command.packageName}: expected=${command.apkMd5} actual=$actualMd5")
                return UpdateResultPayload(
                    taskUuid = command.taskUuid,
                    deviceId = deviceId,
                    success = false,
                    errorMessage = "MD5 mismatch: expected=${command.apkMd5} actual=$actualMd5",
                    installedVersionCode = null
                )
            }

            // Step 3: Silent install via pm
            val installed = systemInstaller.installApk(apkFile)
            if (!installed) {
                apkFile.delete()
                return UpdateResultPayload(
                    taskUuid = command.taskUuid,
                    deviceId = deviceId,
                    success = false,
                    errorMessage = "pm install failed — check SystemInstaller logs",
                    installedVersionCode = null
                )
            }

            // Read back installed version
            val installedVersionCode = try {
                val pkgInfo = context.packageManager.getPackageInfo(command.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
            } catch (e: Exception) {
                null
            }

            apkFile.delete()
            UpdateResultPayload(
                taskUuid = command.taskUuid,
                deviceId = deviceId,
                success = true,
                errorMessage = null,
                installedVersionCode = installedVersionCode
            )
        } catch (e: Exception) {
            apkFile.delete()
            Log.e(TAG, "Install failed for ${command.packageName}: ${e.message}")
            UpdateResultPayload(
                taskUuid = command.taskUuid,
                deviceId = deviceId,
                success = false,
                errorMessage = e.message,
                installedVersionCode = null
            )
        }
    }

    internal suspend fun downloadApk(url: String, dest: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = downloadClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Download failed: HTTP ${response.code}")
        }
        response.body?.byteStream()?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Empty response body")
    }

    internal fun computeMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

}
