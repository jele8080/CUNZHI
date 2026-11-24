package com.example.mqtt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight, read-only LAN MQTT 3.1.1 broker running directly inside the Android app.
 * Accepts CUNZHI01 connections on TCP 1883, replies PUBACK to QoS 1 uplinks on /dpub/<mac>,
 * blocks unauthorized writes to downstream topics, and forwards uplink messages to subscribed clients.
 */
class EmbeddedMqttBroker(
    private val scope: CoroutineScope,
    private val onTelemetryPublish: (topic: String, payload: String, brokerReceivedAtMs: Long) -> Unit,
    private val onClientCountChanged: (Int) -> Unit,
    private val onLog: (title: String, detail: String, isIgnored: Boolean) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val sessions = CopyOnWriteArraySet<ClientSession>()
    private val packetIdCounter = AtomicInteger(1)

    val isRunning: Boolean
        get() = serverSocket?.let { !it.isClosed } == true

    fun start(port: Int, bindAddress: String? = null) {
        stop()

        val server = ServerSocket()
        server.reuseAddress = true
        val socketAddress = if (!bindAddress.isNullOrBlank() && bindAddress != "0.0.0.0") {
            try {
                InetSocketAddress(InetAddress.getByName(bindAddress), port)
            } catch (_: Exception) {
                InetSocketAddress(port)
            }
        } else {
            InetSocketAddress(port)
        }
        server.bind(socketAddress, 32)
        serverSocket = server

        onLog(
            "内置 Broker 已监听",
            "TCP ${server.inetAddress.hostAddress ?: "0.0.0.0"}:${server.localPort}",
            false
        )

        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                try {
                    val clientSocket = server.accept()
                    clientSocket.tcpNoDelay = true
                    clientSocket.soTimeout = 90_000
                    val session = ClientSession(clientSocket)
                    sessions.add(session)
                    onClientCountChanged(sessions.size)
                    launch(Dispatchers.IO) {
                        handleClient(session)
                    }
                } catch (e: SocketException) {
                    if (!server.isClosed && isActive) {
                        onLog("Broker Socket 异常", e.message ?: "连接监听中断", true)
                    }
                    break
                } catch (e: Exception) {
                    if (isActive) {
                        onLog("Broker 接受连接失败", e.message ?: e.javaClass.simpleName, true)
                    }
                }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null

        for (session in sessions) {
            session.close()
        }
        sessions.clear()
        onClientCountChanged(0)
    }

    /**
     * Publishes an authorized downlink command (e.g. /drecv/<mac> or /all) to all connected
     * device sessions subscribed to a matching topic filter.
     * Returns the number of matched subscriber sessions delivered to.
     */
    fun publishDownlink(topic: String, payload: String, qos: Int = 1): Int {
        if (!isRunning) return 0
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        var delivered = 0
        for (session in sessions) {
            val matched = session.subscriptions.any { filter ->
                MqttCodec.topicMatchesFilter(topic, filter)
            }
            if (matched) {
                val packetId = if (qos > 0) nextPacketId() else null
                val packet = MqttPacket.Publish(
                    topic = topic,
                    payload = payloadBytes,
                    qos = qos.coerceIn(0, 1),
                    retain = false,
                    packetId = packetId
                )
                if (session.sendSafe(packet)) {
                    delivered++
                }
            }
        }
        return delivered
    }

    private fun nextPacketId(): Int {
        val next = packetIdCounter.getAndIncrement() and 0xFFFF
        return if (next == 0) 1 else next
    }

    private fun handleClient(session: ClientSession) {
        val remoteIp = session.socket.inetAddress?.hostAddress ?: "unknown"
        var clientId = remoteIp
        try {
            val input = BufferedInputStream(session.socket.getInputStream())
            val firstPacket = MqttCodec.readPacket(input)
            if (firstPacket !is MqttPacket.Connect) {
                onLog("拒绝非 CONNECT 首包", "来自 $remoteIp", true)
                return
            }

            clientId = firstPacket.clientId.ifBlank { remoteIp }
            if (firstPacket.keepAliveSec > 0) {
                // Allow 1.75x keepAlive before timing out idle sockets
                val timeoutMs = ((firstPacket.keepAliveSec * 1750L).coerceIn(30_000L, 180_000L)).toInt()
                session.socket.soTimeout = timeoutMs
            }

            session.send(MqttPacket.ConnAck(sessionPresent = false, returnCode = 0))
            onLog("客户端已接入内置 Broker", "$clientId ($remoteIp)", false)

            while (!session.socket.isClosed) {
                val packet = MqttCodec.readPacket(input)
                when (packet) {
                    is MqttPacket.Publish -> handlePublish(session, clientId, packet)
                    is MqttPacket.Subscribe -> {
                        val grantCodes = packet.subscriptions.map { (filter, requestedQos) ->
                            session.subscriptions.add(filter)
                            requestedQos.coerceAtMost(1)
                        }
                        session.send(MqttPacket.SubAck(packet.packetId, grantCodes))
                    }
                    is MqttPacket.Unsubscribe -> {
                        packet.topics.forEach { session.subscriptions.remove(it) }
                        session.send(MqttPacket.UnsubAck(packet.packetId))
                    }
                    is MqttPacket.PingReq -> session.send(MqttPacket.PingResp)
                    is MqttPacket.PubAck -> Unit
                    is MqttPacket.PubRel -> session.send(MqttPacket.PubComp(packet.packetId))
                    is MqttPacket.Disconnect -> break
                    else -> Unit
                }
            }
        } catch (_: Exception) {
            // Normal EOF or timeout from device disconnect
        } finally {
            session.close()
            sessions.remove(session)
            onClientCountChanged(sessions.size)
            onLog("客户端断开连接", "$clientId ($remoteIp)", false)
        }
    }

    private fun handlePublish(session: ClientSession, clientId: String, packet: MqttPacket.Publish) {
        val receivedAtMs = System.currentTimeMillis()
        // Always acknowledge QoS 1 / QoS 2 promptly so firmware doesn't stall
        when (packet.qos) {
            1 -> packet.packetId?.let { session.send(MqttPacket.PubAck(it)) }
            2 -> packet.packetId?.let { session.send(MqttPacket.PubRec(it)) }
        }

        // Per spec: built-in broker is read-only and only allows device telemetry uplinks on /dpub/<12-hex-mac>
        if (!UPLINK_TOPIC_REGEX.matches(packet.topic.trim())) {
            onLog(
                "已拦截非上行主题发布",
                "$clientId -> ${packet.topic}（内置 Broker 仅允许 /dpub/<mac>）",
                true
            )
            return
        }

        val payloadText = packet.payloadUtf8()
        onTelemetryPublish(packet.topic, payloadText, receivedAtMs)

        // Forward uplink telemetry to any other subscribed monitoring clients
        for (other in sessions) {
            if (other === session) continue
            val matched = other.subscriptions.any { filter ->
                MqttCodec.topicMatchesFilter(packet.topic, filter)
            }
            if (matched) {
                val forwardPacket = MqttPacket.Publish(
                    topic = packet.topic,
                    payload = packet.payload,
                    qos = 0,
                    retain = false
                )
                other.sendSafe(forwardPacket)
            }
        }
    }

    private inner class ClientSession(val socket: Socket) {
        private val outLock = Any()
        private val output = BufferedOutputStream(socket.getOutputStream())
        val subscriptions = CopyOnWriteArraySet<String>()

        fun send(packet: MqttPacket) {
            synchronized(outLock) {
                MqttCodec.writePacket(output, packet)
            }
        }

        fun sendSafe(packet: MqttPacket): Boolean {
            return try {
                send(packet)
                true
            } catch (_: Exception) {
                close()
                false
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private val UPLINK_TOPIC_REGEX = Regex("^/dpub/[0-9a-fA-F]{12}$")
    }
}
