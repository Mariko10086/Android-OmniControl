package com.omnicontrol.agent.mqtt

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.omnicontrol.agent.data.model.mqtt.UpdateCommandPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps the HiveMQ MQTT 3.1.1 async client and exposes a coroutine-friendly API.
 *
 * - [connect] establishes the persistent connection with automatic reconnect.
 * - [publishQos1] / [publishQos0] serialise a data object to JSON and publish.
 * - [subscribeToUpdateCommands] subscribes to the s2d update_command topic and
 *   invokes the provided callback whenever a message is received.
 */
class MqttManager(private val context: Context) {

    companion object {
        private const val TAG = "MqttManager"
        private const val KEEP_ALIVE_SECONDS = 60
    }

    private val gson = Gson()

    private var client: Mqtt3AsyncClient? = null

    private val _connectionState = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Disconnected)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState

    fun isConnected(): Boolean = client?.state?.isConnected == true

    suspend fun connect(host: String, port: Int, clientId: String) {
        if (client?.state?.isConnected == true) return
        _connectionState.value = MqttConnectionState.Connecting
        Log.d(TAG, "Connecting to $host:$port as $clientId")

        client = Mqtt3Client.builder()
            .identifier(clientId)
            .serverHost(host)
            .serverPort(port)
            .automaticReconnect()
                .initialDelay(3, TimeUnit.SECONDS)
                .maxDelay(120, TimeUnit.SECONDS)
                .applyAutomaticReconnect()
            .addConnectedListener {
                Log.i(TAG, "MQTT connected")
                _connectionState.value = MqttConnectionState.Connected
            }
            .addDisconnectedListener { ctx ->
                val cause = ctx.cause
                if (cause != null) {
                    Log.w(TAG, "MQTT disconnected with error: ${cause.message}")
                    _connectionState.value = MqttConnectionState.Error(cause)
                } else {
                    Log.i(TAG, "MQTT disconnected")
                    _connectionState.value = MqttConnectionState.Disconnected
                }
            }
            .buildAsync()

        suspendCancellableCoroutine<Unit> { cont ->
            client!!.connectWith()
                .cleanSession(true)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .send()
                .whenComplete { _, ex ->
                    if (ex != null) {
                        Log.e(TAG, "MQTT connect failed: ${ex.message}")
                        _connectionState.value = MqttConnectionState.Error(ex)
                        if (cont.isActive) cont.resumeWithException(ex)
                    } else {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }

            cont.invokeOnCancellation {
                client?.disconnect()
            }
        }
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _connectionState.value = MqttConnectionState.Disconnected
    }

    /** Publishes [payload] serialised as JSON with QoS 1 (at-least-once delivery). */
    suspend fun publishQos1(topic: String, payload: Any) {
        publish(topic, payload, MqttQos.AT_LEAST_ONCE)
    }

    /** Publishes [payload] serialised as JSON with QoS 0 (fire-and-forget). */
    suspend fun publishQos0(topic: String, payload: Any) {
        publish(topic, payload, MqttQos.AT_MOST_ONCE)
    }

    private suspend fun publish(topic: String, payload: Any, qos: MqttQos) {
        val c = client ?: throw IllegalStateException("MQTT client not connected")
        val jsonString = gson.toJson(payload)
        Log.i(TAG, "MQTT_UPLOAD topic=$topic qos=${qos.code} payload=$jsonString")
        val json = jsonString.toByteArray(Charsets.UTF_8)

        suspendCancellableCoroutine<Unit> { cont ->
            c.publishWith()
                .topic(topic)
                .payload(json)
                .qos(qos)
                .send()
                .whenComplete { _, ex ->
                    if (ex != null) {
                        Log.w(TAG, "Publish failed on $topic: ${ex.message}")
                        if (cont.isActive) cont.resumeWithException(ex)
                    } else {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
        }
    }

    /**
     * Subscribes to the server-to-device update_command topic for [deviceId].
     * [onCommand] is invoked on the MQTT client thread for each received message;
     * callers should dispatch to a coroutine scope for non-trivial work.
     */
    fun subscribeToUpdateCommands(deviceId: String, onCommand: (UpdateCommandPayload) -> Unit) {
        val c = client ?: return
        val topic = MqttTopics.updateCommand(deviceId)

        c.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
            .whenComplete { _, ex ->
                if (ex != null) {
                    Log.e(TAG, "Subscribe to $topic failed: ${ex.message}")
                } else {
                    Log.i(TAG, "Subscribed to $topic")
                }
            }

        c.publishes(MqttGlobalPublishFilter.SUBSCRIBED) { publish ->
            val buf: ByteBuffer = publish.payload.orElse(null) ?: return@publishes
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            val json = String(bytes, Charsets.UTF_8)
            try {
                val command = gson.fromJson(json, UpdateCommandPayload::class.java)
                onCommand(command)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse update_command: ${e.message}")
            }
        }
    }
}

