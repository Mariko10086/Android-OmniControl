package com.omnicontrol.agent.collector

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.omnicontrol.agent.data.model.DeviceInfo
import com.omnicontrol.agent.data.prefs.DevicePreferences
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class DeviceInfoCollector(private val context: Context) {

    private val prefs = DevicePreferences(context)

    fun collect(): DeviceInfo {
        val tmpOk = checkDirWritable(File("/data/local/tmp"))
        val sdcardOk = checkDirWritable(File("/sdcard/mock"))
        return DeviceInfo(
            deviceId = prefs.getOrCreateDeviceId(),
            deviceName = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            serialNumber = getSerialNumber(),
            imei = getImei(),
            ipAddress = getLocalIpAddress(),
            systemLanguage = getSystemLanguage(),
            fileWriteCheck = tmpOk && sdcardOk,
            fileWriteCheckTmp = tmpOk,
            fileWriteCheckSdcard = sdcardOk
        )
    }

    @SuppressLint("HardwareIds")
    private fun getSerialNumber(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Build.getSerial().takeIf { it != Build.UNKNOWN } ?: ""
                } else {
                    ""
                }
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL.takeIf { it != Build.UNKNOWN } ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getImei(): String {
        return try {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return ""
            }
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.imei ?: ""
            } else {
                @Suppress("DEPRECATION")
                tm.deviceId ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getSystemLanguage(): String {
        return Locale.getDefault().toLanguageTag()
    }

    /** 测试指定目录是否可写（目录不存在时尝试创建） */
    private fun checkDirWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val tmp = File.createTempFile("oc_probe_", ".tmp", dir)
            tmp.writeText("1")
            tmp.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkFileWrite(): Boolean {
        val tmp = checkDirWritable(File("/data/local/tmp"))
        val sdcard = checkDirWritable(File("/sdcard/mock"))
        return tmp && sdcard
    }

    private fun getLocalIpAddress(): String {
        return try {
            // Primary: read from WifiManager (fast, reliable on Wi-Fi)
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val wifiIp = wm.connectionInfo.ipAddress
            if (wifiIp != 0) {
                return "%d.%d.%d.%d".format(
                    wifiIp and 0xff,
                    wifiIp shr 8 and 0xff,
                    wifiIp shr 16 and 0xff,
                    wifiIp shr 24 and 0xff
                )
            }
            // Fallback: iterate NetworkInterface for first non-loopback IPv4
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
