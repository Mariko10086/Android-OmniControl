package com.omnicontrol.agent.mqtt

sealed class MqttConnectionState {
    object Disconnected : MqttConnectionState()
    object Connecting   : MqttConnectionState()
    object Connected    : MqttConnectionState()
    data class Error(val cause: Throwable) : MqttConnectionState()
}
