package com.omnicontrol.agent

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.omnicontrol.agent.databinding.ActivityDashboardBinding
import com.omnicontrol.agent.system.ScreenKeepAwake
import com.omnicontrol.agent.ui.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and show above lockscreen — takes effect immediately in this Activity
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        requestBatteryOptimizationExemption()

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupObservers()
        viewModel.loadDashboard()

        // Apply persistent system-level settings via root shell, then refresh status
        lifecycleScope.launch(Dispatchers.IO) {
            val applied = ScreenKeepAwake.applySystemSettings()
            val status = ScreenKeepAwake.queryCurrentStatus(this@MainActivity, applied)
            withContext(Dispatchers.Main) {
                viewModel.updateScreenStatus(status)
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDashboard()
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            binding.progressBar.visibility =
                if (state.isLoading) View.VISIBLE else View.GONE

            state.deviceInfo?.let { d ->
                binding.textDeviceId.text = "Device ID: ${d.deviceId}"
                binding.textModel.text = "Model: ${d.manufacturer} ${d.model}"
                binding.textAndroidVersion.text =
                    "Android: ${d.androidVersion} (API ${d.sdkVersion})"
                binding.textBrand.text = "Language: ${d.systemLanguage}"

                binding.textFileWriteTmp.text =
                    "/data/local/tmp  ${if (d.fileWriteCheckTmp) "✓ 可写" else "✗ 不可写"}"
                binding.textFileWriteSdcard.text =
                    "/sdcard/mock     ${if (d.fileWriteCheckSdcard) "✓ 可写" else "✗ 不可写"}"
            }

            state.storageInfo?.let { s ->
                val intPct = "%.1f".format(s.internal.usedPercent)
                binding.textInternalStorage.text =
                    "Internal: ${formatBytes(s.internal.usedBytes)} / " +
                    "${formatBytes(s.internal.totalBytes)} ($intPct% used)\n" +
                    "Available: ${formatBytes(s.internal.availableBytes)}"

                binding.textSdCard.text = if (s.externalSd != null) {
                    val extPct = "%.1f".format(s.externalSd.usedPercent)
                    "SD Card: ${formatBytes(s.externalSd.usedBytes)} / " +
                    "${formatBytes(s.externalSd.totalBytes)} ($extPct% used)"
                } else {
                    "SD Card: Not present"
                }
            }

            if (state.appStatuses.isNotEmpty()) {
                val appsText = state.appStatuses.joinToString("\n") { app ->
                    val status = if (app.installed) {
                        val enabled = if (app.enabled == true) "enabled" else "disabled"
                        "INSTALLED  v${app.versionName}  [$enabled]"
                    } else {
                        "NOT INSTALLED"
                    }
                    "${app.packageName}\n  $status"
                }
                binding.textAppStatuses.text = appsText
            } else {
                binding.textAppStatuses.text = "No packages configured"
            }

            state.screenAwakeStatus?.let { s ->
                val timeoutText = if (s.screenTimeoutMs == Int.MAX_VALUE.toLong()) {
                    "Screen Timeout: Never (max)"
                } else if (s.screenTimeoutMs < 0) {
                    "Screen Timeout: Unknown"
                } else {
                    "Screen Timeout: ${s.screenTimeoutMs / 1000}s"
                }
                binding.textScreenTimeout.text = timeoutText
                binding.textLockScreenStatus.text =
                    "Lockscreen: ${if (s.lockScreenDisabled) "Disabled ✓" else "Enabled"}"
            }

            if (state.errorMessage != null) {
                binding.textError.text = "Error: ${state.errorMessage}"
                binding.textError.visibility = View.VISIBLE
            } else {
                binding.textError.visibility = View.GONE
            }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L     -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L         -> "%.1f KB".format(bytes / 1_000.0)
        else                    -> "$bytes B"
    }
}
