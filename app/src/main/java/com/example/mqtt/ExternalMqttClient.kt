package com.example.mqtt

import com.example.model.CunzhiProtocolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

data class ParsedBrokerEndpoint(
    val host: String,
    val port: Int
) {
    val displayUrl: String get() = "mqtt://$host:$port"
}

/**
 * Pure-Kotlin MQTT 3.1.1 client for subscribing to an existing LAN broker (/dpub/+, QoS 1).
 * Automatically reconnects with bounded backoff and re-subscribes on every reconnection.
 */
class ExternalMqttClient(
    private val scope: CoroutineScope,
    private val onTelemetryPublish: (topic: String, payload: String, brokerReceivedAtMs: Long) -> Unit,
    private val onStateChanged: (connected: Boolean, reconnectAttempts: Int, errorMessage: String?) -> Unit,
    private val onLog: (title: String, detail: String, isIgnored: Boolean) -> Unit
) {
    private var runnerJob: Job? = null
    @Volatile
    private var activeSocket: Socket? = null
    @Volatile
    private var activeSender: ((MqttPacket) -> Unit)? = null
    private val packetIdSeq = AtomicInteger(1)

    fun publish(topic: String, payload: String, qos: Int = 1): Boolean {
        val sender = activeSender ?: return false
        return try {
            val packetId = if (qos > 0) nextPacketId() else null
            sender(
                MqttPacket.Publish(
                    topic = topic,
                    payload = payload.toByteArray(Charsets.UTF_8),
                    qos = qos.coerceIn(0, 1),
                    retain = false,
                    packetId = packetId
                )
            )
            true
        } catch (e: Exception) {
            onLog("外部 Broker 下发失败", "$topic · ${e.message ?: e.javaClass.simpleName}", true)
            false
        }
    }

    fun start(
        rawUrl: String,
        username: String = "",
        password: String = ""
    ) {
        stop()
        val endpoint = parseEndpoint(rawUrl)
        if (endpoint == null) {
            onStateChanged(false, 0, "无效的 Broker 地址: $rawUrl")
            onLog("外部 Broker 地址格式错误", rawUrl, true)
            return
        }

        runnerJob = scope.launch(Dispatchers.IO) {
            var attempts = 0
            while (isActive) {
                var pingJob: Job? = null
                try {
                    if (attempts > 0) {
                        val backoffMs = minOf(10_000L, 1_500L * (1L shl (attempts - 1).coerceAtMost(3)))
                        onStateChanged(false, attempts, "正在重连 (${backoffMs / 1000}s 后)...")
                        delay(backoffMs)
                    }
                    attempts++

                    val socket = Socket()
                    activeSocket = socket
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(endpoint.host, endpoint.port), 6_000)
                    socket.soTimeout = 45_000

                    val input = BufferedInputStream(socket.getInputStream())
                    val output = BufferedOutputStream(socket.getOutputStream())
                    val outLock = Any()

                    fun send(packet: MqttPacket) {
                        synchronized(outLock) {
                            MqttCodec.writePacket(output, packet)
                        }
                    }

                    val clientId = "cunzhi-apk-" + UUID.randomUUID().toString().take(8)
                    send(
                        MqttPacket.Connect(
                            protocolName = "MQTT",
                            protocolLevel = 4,
                            cleanSession = true,
                            keepAliveSec = 20,
                            clientId = clientId,
                            username = username.ifBlank { null },
                            password = password.ifBlank { null }
                        )
                    )

                    val connAck = MqttCodec.readPacket(input)
                    if (connAck !is MqttPacket.ConnAck || connAck.returnCode != 0) {
                        val code = (connAck as? MqttPacket.ConnAck)?.returnCode
                        throw IOException("Broker 拒绝连接 (CONNACK=$code)")
                    }

                    // Mandatory per spec: re-subscribe to /dpub/+ on every connect/reconnect
                    val subPacketId = nextPacketId()
                    send(
                        MqttPacket.Subscribe(
                            packetId = subPacketId,
                            subscriptions = listOf(CunzhiProtocolSchema.UPLINK_SUBSCRIPTION_FILTER to 1)
                        )
                    )

                    val subAck = MqttCodec.readPacket(input)
                    if (subAck !is MqttPacket.SubAck || subAck.returnCodes.any { it == 0x80 }) {
                        throw IOException("订阅 ${CunzhiProtocolSchema.UPLINK_SUBSCRIPTION_FILTER} 失败")
                    }

                    attempts = 0
                    activeSender = ::send
                    onStateChanged(true, 0, null)
                    onLog(
                        "已连接外部 Broker 并订阅",
                        "${endpoint.displayUrl} · 订阅 ${CunzhiProtocolSchema.UPLINK_SUBSCRIPTION_FILTER} (QoS 1)",
                        false
                    )

                    pingJob = launch(Dispatchers.IO) {
                        while (isActive && !socket.isClosed) {
                            delay(15_000L)
                            try {
                                send(MqttPacket.PingReq)
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }

                    while (isActive && !socket.isClosed) {
                        val packet = MqttCodec.readPacket(input)
                        when (packet) {
                            is MqttPacket.Publish -> {
                                val receivedAt = System.currentTimeMillis()
                                if (packet.qos == 1) {
                                    packet.packetId?.let { send(MqttPacket.PubAck(it)) }
                                } else if (packet.qos == 2) {
                                    packet.packetId?.let { send(MqttPacket.PubRec(it)) }
                                }
                                onTelemetryPublish(packet.topic, packet.payloadUtf8(), receivedAt)
                            }
                            is MqttPacket.PubRel -> send(MqttPacket.PubComp(packet.packetId))
                            is MqttPacket.PingResp -> Unit
                            else -> Unit
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        val reason = e.message ?: e.javaClass.simpleName
                        onStateChanged(false, attempts, reason)
                        onLog("外部 Broker 连接中断", "${endpoint.displayUrl} · $reason", true)
                    }
                } finally {
                    activeSender = null
                    pingJob?.cancel()
                    try {
                        activeSocket?.close()
                    } catch (_: Exception) {
                    }
                    activeSocket = null
                }
            }
        }
    }

    fun stop() {
        activeSender = null
        runnerJob?.cancel()
        runnerJob = null
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        }
        activeSocket = null
    }

    private fun nextPacketId(): Int {
        val id = packetIdSeq.getAndIncrement() and 0xFFFF
        return if (id == 0) 1 else id
    }

    companion object {
        fun parseEndpoint(rawUrl: String): ParsedBrokerEndpoint? {
            val cleaned = rawUrl.trim()
                .removePrefix("mqtt://")
                .removePrefix("tcp://")
                .substringBefore("/")
                .trim()
            if (cleaned.isBlank()) return null

            val lastColon = cleaned.lastIndexOf(':')
            return if (lastColon > 0 && lastColon < cleaned.length - 1) {
                val host = cleaned.substring(0, lastColon).trim()
                val port = cleaned.substring(lastColon + 1).trim().toIntOrNull()
                if (host.isBlank() || port == null || port !in 1..65535) null
                else ParsedBrokerEndpoint(host, port)
            } else {
                ParsedBrokerEndpoint(cleaned, CunzhiProtocolSchema.DEFAULT_MQTT_PORT)
            }
        }
    }
}
