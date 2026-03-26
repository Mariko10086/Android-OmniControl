package com.omnicontrol.agent.mqtt

/** Centralises all MQTT topic string construction. */
object MqttTopics {
    private const val D2S = "omnicontrol/d2s"
    private const val S2D = "omnicontrol/s2d"

    fun register(deviceId: String)      = "$D2S/$deviceId/register"
    fun deviceInfo(deviceId: String)    = "$D2S/$deviceId/device_info"
    fun appStatus(deviceId: String)     = "$D2S/$deviceId/app_status"
    fun updateAck(deviceId: String)     = "$D2S/$deviceId/update_ack"
    fun updateResult(deviceId: String)  = "$D2S/$deviceId/update_result"
    fun heartbeat(deviceId: String)     = "$D2S/$deviceId/heartbeat"
    fun updateCommand(deviceId: String) = "$S2D/$deviceId/update_command"
}
