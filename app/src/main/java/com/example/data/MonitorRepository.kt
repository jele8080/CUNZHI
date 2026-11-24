package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.model.ConnectionStatus
import com.example.model.CunzhiProtocolSchema
import com.example.model.DownlinkFormat
import com.example.model.DownlinkResult
import com.example.model.LogCategory
import com.example.model.MonitorConfig
import com.example.model.MonitorMode
import com.example.model.RuntimeStatus
import com.example.mqtt.EmbeddedMqttBroker
import com.example.mqtt.ExternalMqttClient
import com.example.mqtt.MdnsCodec
import com.example.mqtt.MdnsResponder
import com.example.mqtt.NetworkUtils
import com.example.telemetry.DownlinkCommandBuilder
import com.example.telemetry.TelemetryEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Application-scoped singleton coordinating TelemetryEngine, EmbeddedMqttBroker,
 * ExternalMqttClient, MdnsResponder, and Wi-Fi network change auto-recovery.
 */
class MonitorRepository private constructor(private val appContext: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val configStore = ConfigStore(appContext)
    val telemetryEngine = TelemetryEngine()

    private val _runtimeStatus = MutableStateFlow(
        RuntimeStatus(detectedLocalIps = NetworkUtils.getLocalIpv4Addresses())
    )
    val runtimeStatus: StateFlow<RuntimeStatus> = _runtimeStatus.asStateFlow()

    @Volatile
    private var activeConfig: MonitorConfig? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingRecoveryJob: Job? = null
    private val msgIdSeq = AtomicInteger(1)

    private val embeddedBroker = EmbeddedMqttBroker(
        scope = scope,
        onTelemetryPublish = { topic, payload, brokerReceivedAt ->
            telemetryEngine.ingest(
                topic = topic,
                payload = payload,
                brokerReceivedAtMs = brokerReceivedAt,
                appliedAtMs = System.currentTimeMillis()
            )
        },
        onClientCountChanged = { count ->
            _runtimeStatus.update { it.copy(connectedClients = count) }
        },
        onLog = { title, detail, isIgnored ->
            telemetryEngine.recordLog(
                category = if (isIgnored) LogCategory.IGNORED else LogCategory.SYSTEM,
                title = title,
                detail = detail
            )
        }
    )

    private val externalClient = ExternalMqttClient(
        scope = scope,
        onTelemetryPublish = { topic, payload, receivedAt ->
            telemetryEngine.ingest(
                topic = topic,
                payload = payload,
                brokerReceivedAtMs = receivedAt,
                appliedAtMs = System.currentTimeMillis()
            )
        },
        onStateChanged = { connected, reconnects, error ->
            _runtimeStatus.update { current ->
                if (current.mode != MonitorMode.EXTERNAL || current.status == ConnectionStatus.STOPPED) {
                    current
                } else {
                    current.copy(
                        mqttReady = connected,
                        status = when {
                            connected -> ConnectionStatus.RUNNING
                            reconnects > 0 -> ConnectionStatus.RECONNECTING
                            error != null -> ConnectionStatus.ERROR
                            else -> ConnectionStatus.STARTING
                        },
                        reconnectAttempts = reconnects,
                        errorMessage = error
                    )
                }
            }
        },
        onLog = { title, detail, isIgnored ->
            telemetryEngine.recordLog(
                category = if (isIgnored) LogCategory.IGNORED else LogCategory.SYSTEM,
                title = title,
                detail = detail
            )
        }
    )

    private val mdnsResponder = MdnsResponder(
        context = appContext,
        scope = scope,
        onResponseSent = { count ->
            _runtimeStatus.update { it.copy(mdnsResponses = count) }
        },
        onLog = { title, detail, isError ->
            telemetryEngine.recordLog(
                category = if (isError) LogCategory.IGNORED else LogCategory.MDNS,
                title = title,
                detail = detail
            )
        }
    )

    fun refreshLocalIps(): List<String> {
        val ips = NetworkUtils.getLocalIpv4Addresses()
        _runtimeStatus.update { it.copy(detectedLocalIps = ips) }
        return ips
    }

    fun updateLockStatus(wakeLockActive: Boolean, wifiLockActive: Boolean) {
        _runtimeStatus.update {
            it.copy(wakeLockActive = wakeLockActive, wifiLockActive = wifiLockActive)
        }
    }

    fun updateAutoRecoverPreference(enabled: Boolean) {
        activeConfig = activeConfig?.copy(autoRecoverNetwork = enabled)
        if (enabled && _runtimeStatus.value.status != ConnectionStatus.STOPPED) {
            registerNetworkWatcher()
        } else {
            unregisterNetworkWatcher()
        }
    }

    /**
     * Used when Android / Xiaomi MIUI restarts MqttMonitorService via START_STICKY with null intent.
     */
    fun ensureStartedFromPersistedConfig() {
        if (_runtimeStatus.value.status != ConnectionStatus.STOPPED) return
        scope.launch {
            if (_runtimeStatus.value.status != ConnectionStatus.STOPPED) return@launch
            val config = configStore.loadCurrent()
            telemetryEngine.recordLog(
                LogCategory.SYSTEM,
                "后台服务自动拉起恢复",
                "已按本地配置自动恢复 ${config.mode.label}"
            )
            startInternal(config)
        }
    }

    fun start(config: MonitorConfig) {
        scope.launch {
            startInternal(config)
        }
    }

    fun stop() {
        scope.launch {
            stopInternal(logStop = true)
        }
    }

    fun sendGetCommand(mac: String, key: String): DownlinkResult {
        val msgId = nextMsgId()
        val topic = DownlinkCommandBuilder.downlinkTopic(mac)
        val payload = DownlinkCommandBuilder.buildGetCommand(key = key, msgId = msgId)
        return dispatchDownlink(topic, payload, msgId, "查询属性 get ($mac)")
    }

    fun sendSetCommand(
        mac: String,
        key: String,
        value: Any,
        format: DownlinkFormat = activeConfig?.downlinkFormat ?: DownlinkFormat.KEY_VALUE
    ): DownlinkResult {
        val msgId = nextMsgId()
        val topic = DownlinkCommandBuilder.downlinkTopic(mac)
        val payload = DownlinkCommandBuilder.buildSetCommand(key = key, value = value, msgId = msgId, format = format)
        return dispatchDownlink(topic, payload, msgId, "写入属性 set ($mac · $key=$value)")
    }

    fun sendEmergencyStop(
        mac: String,
        format: DownlinkFormat = activeConfig?.downlinkFormat ?: DownlinkFormat.KEY_VALUE
    ): DownlinkResult {
        val rPower = sendSetCommand(mac, "power", 0, format)
        val rShock = sendSetCommand(mac, "shock", 0, format)
        val rVolt = sendSetCommand(mac, "voltage", 0, format)
        val ok = rPower.success || rShock.success || rVolt.success
        return DownlinkResult(
            success = ok,
            topic = rPower.topic,
            payload = "power=0, shock=0, voltage=0",
            message = if (ok) "已发送急停归零指令 (power/shock/voltage = 0)" else rPower.message,
            msgId = rVolt.msgId
        )
    }

    private fun dispatchDownlink(
        topic: String,
        payload: String,
        msgId: Int,
        logTitle: String
    ): DownlinkResult {
        val status = _runtimeStatus.value
        if (status.status == ConnectionStatus.STOPPED) {
            val msg = "服务未启动，无法下发指令"
            telemetryEngine.recordLog(LogCategory.IGNORED, "$logTitle 失败", msg)
            return DownlinkResult(false, topic, payload, msg, msgId)
        }

        return when (status.mode) {
            MonitorMode.EMBEDDED -> {
                val delivered = embeddedBroker.publishDownlink(topic, payload, qos = 1)
                if (delivered > 0) {
                    val msg = "已直达 $delivered 个订阅设备 (msg_id=$msgId)"
                    telemetryEngine.recordLog(LogCategory.DOWNLINK, logTitle, "$topic -> $payload ($msg)")
                    DownlinkResult(true, topic, payload, msg, msgId)
                } else {
                    val msg = "当前没有已订阅 $topic 的在线设备连接"
                    telemetryEngine.recordLog(LogCategory.IGNORED, "$logTitle 未送达", "$topic -> $payload ($msg)")
                    DownlinkResult(false, topic, payload, msg, msgId)
                }
            }
            MonitorMode.EXTERNAL -> {
                val sent = externalClient.publish(topic, payload, qos = 1)
                if (sent) {
                    val msg = "已通过外部 Broker 下发 (msg_id=$msgId)"
                    telemetryEngine.recordLog(LogCategory.DOWNLINK, logTitle, "$topic -> $payload")
                    DownlinkResult(true, topic, payload, msg, msgId)
                } else {
                    val msg = "外部 Broker 未连接，指令发送失败"
                    telemetryEngine.recordLog(LogCategory.IGNORED, "$logTitle 失败", "$topic · $msg")
                    DownlinkResult(false, topic, payload, msg, msgId)
                }
            }
        }
    }

    private fun nextMsgId(): Int {
        val next = msgIdSeq.getAndIncrement()
        return if (next <= 0 || next > 999_999) {
            msgIdSeq.set(1)
            1
        } else {
            next
        }
    }

    private fun startInternal(config: MonitorConfig) {
        stopInternal(logStop = false)
        activeConfig = config
        val localIps = NetworkUtils.getLocalIpv4Addresses()
        when (config.mode) {
            MonitorMode.EMBEDDED -> startEmbedded(config, localIps)
            MonitorMode.EXTERNAL -> startExternal(config, localIps)
        }
        if (config.autoRecoverNetwork) {
            registerNetworkWatcher()
        }
    }

    private fun startEmbedded(config: MonitorConfig, localIps: List<String>) {
        val port = config.embeddedPort.coerceIn(1024, 65535)
        val overrideIp = config.mdnsIpOverride.trim()
        val chosenIp = when {
            overrideIp.isNotEmpty() && MdnsCodec.parseIpv4Bytes(overrideIp) != null -> overrideIp
            localIps.isNotEmpty() -> localIps.first()
            else -> null
        }

        val currentLocks = _runtimeStatus.value
        _runtimeStatus.value = RuntimeStatus(
            mode = MonitorMode.EMBEDDED,
            status = ConnectionStatus.STARTING,
            mqttReady = false,
            brokerPort = port,
            discoveryIp = chosenIp,
            mdnsActive = false,
            mdnsResponses = 0,
            activeEndpoint = "${chosenIp ?: "0.0.0.0"}:$port",
            connectedClients = 0,
            detectedLocalIps = localIps,
            wakeLockActive = currentLocks.wakeLockActive,
            wifiLockActive = currentLocks.wifiLockActive,
            networkRecoveries = currentLocks.networkRecoveries
        )

        try {
            embeddedBroker.start(port = port, bindAddress = "0.0.0.0")
            val mdnsStarted = if (config.mdnsEnabled && chosenIp != null) {
                mdnsResponder.start(chosenIp, CunzhiProtocolSchema.MDNS_HOST)
            } else {
                if (config.mdnsEnabled && chosenIp == null) {
                    telemetryEngine.recordLog(
                        LogCategory.MDNS,
                        "未检测到局域网 IPv4",
                        "请连接 Wi-Fi 或开启个人热点后刷新 IP，以便宣告 easysmart.local"
                    )
                }
                false
            }

            _runtimeStatus.update {
                it.copy(
                    status = ConnectionStatus.RUNNING,
                    mqttReady = true,
                    mdnsActive = mdnsStarted,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            embeddedBroker.stop()
            mdnsResponder.stop()
            val reason = e.message ?: "端口 $port 启动失败"
            telemetryEngine.recordLog(LogCategory.IGNORED, "内置 Broker 启动失败", reason)
            _runtimeStatus.update {
                it.copy(
                    status = ConnectionStatus.ERROR,
                    mqttReady = false,
                    mdnsActive = false,
                    errorMessage = reason
                )
            }
        }
    }

    private fun startExternal(config: MonitorConfig, localIps: List<String>) {
        val parsed = ExternalMqttClient.parseEndpoint(config.externalBrokerUrl)
        val currentLocks = _runtimeStatus.value
        _runtimeStatus.value = RuntimeStatus(
            mode = MonitorMode.EXTERNAL,
            status = ConnectionStatus.STARTING,
            mqttReady = false,
            brokerPort = parsed?.port,
            discoveryIp = null,
            mdnsActive = false,
            mdnsResponses = 0,
            activeEndpoint = parsed?.displayUrl ?: config.externalBrokerUrl,
            connectedClients = 0,
            reconnectAttempts = 0,
            errorMessage = null,
            detectedLocalIps = localIps,
            wakeLockActive = currentLocks.wakeLockActive,
            wifiLockActive = currentLocks.wifiLockActive,
            networkRecoveries = currentLocks.networkRecoveries
        )

        externalClient.start(
            rawUrl = config.externalBrokerUrl,
            username = config.externalUsername,
            password = config.externalPassword
        )
    }

    private fun stopInternal(logStop: Boolean) {
        val wasRunning = _runtimeStatus.value.status != ConnectionStatus.STOPPED
        unregisterNetworkWatcher()
        activeConfig = null
        embeddedBroker.stop()
        externalClient.stop()
        mdnsResponder.stop()

        _runtimeStatus.update { current ->
            current.copy(
                status = ConnectionStatus.STOPPED,
                mqttReady = false,
                mdnsActive = false,
                connectedClients = 0,
                reconnectAttempts = 0,
                errorMessage = null
            )
        }
        if (logStop && wasRunning) {
            telemetryEngine.recordLog(LogCategory.SYSTEM, "监看服务已停止", "已关闭监听与连接")
        }
    }

    private fun registerNetworkWatcher() {
        unregisterNetworkWatcher()
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleNetworkRecovery("Wi-Fi 网络已就绪")
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                scheduleNetworkRecovery("Wi-Fi 链路/IP 变化")
            }

            override fun onLost(network: Network) {
                refreshLocalIps()
                telemetryEngine.recordLog(
                    LogCategory.SYSTEM,
                    "Wi-Fi 连接暂时断开",
                    "等待网络恢复后将自动重建 mDNS 宣告与监听"
                )
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkWatcher() {
        pendingRecoveryJob?.cancel()
        pendingRecoveryJob = null
        val cb = networkCallback ?: return
        networkCallback = null
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        }
    }

    private fun scheduleNetworkRecovery(reason: String) {
        pendingRecoveryJob?.cancel()
        pendingRecoveryJob = scope.launch {
            delay(1_200L) // Debounce bursty link-property updates on MIUI/HyperOS
            val config = activeConfig ?: return@launch
            val current = _runtimeStatus.value
            if (current.status == ConnectionStatus.STOPPED) return@launch

            val freshIps = NetworkUtils.getLocalIpv4Addresses()
            _runtimeStatus.update { it.copy(detectedLocalIps = freshIps) }

            if (config.mode == MonitorMode.EMBEDDED && embeddedBroker.isRunning) {
                val overrideIp = config.mdnsIpOverride.trim()
                val desiredIp = when {
                    overrideIp.isNotEmpty() && MdnsCodec.parseIpv4Bytes(overrideIp) != null -> overrideIp
                    freshIps.isNotEmpty() -> freshIps.first()
                    else -> null
                }
                val ipChanged = desiredIp != current.discoveryIp
                if (config.mdnsEnabled && desiredIp != null && (ipChanged || !current.mdnsActive)) {
                    val restarted = mdnsResponder.start(desiredIp, CunzhiProtocolSchema.MDNS_HOST)
                    val port = current.brokerPort ?: config.embeddedPort
                    _runtimeStatus.update {
                        it.copy(
                            discoveryIp = desiredIp,
                            activeEndpoint = "$desiredIp:$port",
                            mdnsActive = restarted,
                            networkRecoveries = it.networkRecoveries + 1
                        )
                    }
                    telemetryEngine.recordLog(
                        LogCategory.MDNS,
                        "网络恢复自愈：$reason",
                        "已重新绑定 easysmart.local -> $desiredIp"
                    )
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: MonitorRepository? = null

        fun getInstance(context: Context): MonitorRepository {
            return instance ?: synchronized(this) {
                instance ?: MonitorRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
