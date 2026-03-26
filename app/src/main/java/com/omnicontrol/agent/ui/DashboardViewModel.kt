package com.omnicontrol.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.omnicontrol.agent.collector.AppStatusCollector
import com.omnicontrol.agent.collector.DeviceInfoCollector
import com.omnicontrol.agent.collector.StorageInfoCollector
import com.omnicontrol.agent.config.AppConfig
import com.omnicontrol.agent.mqtt.MqttManagerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableLiveData(DashboardUiState())
    val uiState: LiveData<DashboardUiState> = _uiState

    private val context = application.applicationContext

    init {
        observeMqttState()
    }

    fun loadDashboard() {
        _uiState.value = _uiState.value?.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                try {
                    val deviceInfo = DeviceInfoCollector(context).collect()
                    val storageInfo = StorageInfoCollector(context).collect()
                    val packages = AppConfig.getTargetPackages(context)
                    val appStatuses = AppStatusCollector(context).collect(packages)
                    _uiState.value?.copy(
                        isLoading = false,
                        deviceInfo = deviceInfo,
                        storageInfo = storageInfo,
                        appStatuses = appStatuses,
                        errorMessage = null
                    ) ?: DashboardUiState(
                        isLoading = false,
                        deviceInfo = deviceInfo,
                        storageInfo = storageInfo,
                        appStatuses = appStatuses
                    )
                } catch (e: Exception) {
                    _uiState.value?.copy(isLoading = false, errorMessage = e.message)
                        ?: DashboardUiState(isLoading = false, errorMessage = e.message)
                }
            }
            _uiState.value = state
        }
    }

    private fun observeMqttState() {
        viewModelScope.launch {
            MqttManagerHolder.get(context).connectionState.collect { mqttState ->
                _uiState.value = _uiState.value?.copy(mqttConnectionState = mqttState)
            }
        }
    }
}
