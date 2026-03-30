package com.omnicontrol.agent.ui

import com.omnicontrol.agent.data.model.AppStatusInfo
import com.omnicontrol.agent.data.model.DeviceInfo
import com.omnicontrol.agent.data.model.StorageInfo
import com.omnicontrol.agent.mqtt.MqttConnectionState
import com.omnicontrol.agent.system.ScreenAwakeStatus

data class DashboardUiState(
    val isLoading: Boolean = false,
    val deviceInfo: DeviceInfo? = null,
    val storageInfo: StorageInfo? = null,
    val appStatuses: List<AppStatusInfo> = emptyList(),
    val mqttConnectionState: MqttConnectionState = MqttConnectionState.Disconnected,
    val screenAwakeStatus: ScreenAwakeStatus? = null,
    val errorMessage: String? = null
)
