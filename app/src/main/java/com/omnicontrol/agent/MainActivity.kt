package com.omnicontrol.agent

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.omnicontrol.agent.databinding.ActivityDashboardBinding
import com.omnicontrol.agent.ui.DashboardViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupObservers()
        viewModel.loadDashboard()
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
                    "Android: ${d.androidVersion} (API ${d.sdkInt})"
                binding.textBrand.text = "Brand: ${d.brand} / ${d.product}"
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
