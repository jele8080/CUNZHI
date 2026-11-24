package com.example.model

enum class MonitorMode(val apiName: String, val label: String) {
    EMBEDDED("embedded", "内置 Broker"),
    EXTERNAL("external", "已有 Broker")
}

enum class ConnectionStatus(val label: String) {
    STOPPED("已停止"),
    STARTING("启动中"),
    RUNNING("运行中"),
    RECONNECTING("重连中"),
    ERROR("异常")
}

enum class FieldType {
    STRING,
    INT,
    FLOAT
}

enum class FieldGroup(val label: String) {
    BASIC("基本与心跳"),
    PRESSURE("压力与阈值"),
    MOTOR_STIM("电机与电刺激"),
    GAME_TIMING("模式与计时")
}

data class DeviceFieldSpec(
    val key: String,
    val label: String,
    val type: FieldType,
    val writable: Boolean,
    val unit: String = "",
    val defaultHint: String = "",
    val description: String,
    val group: FieldGroup
)

object CunzhiProtocolSchema {
    const val MAX_HISTORY_POINTS = 180
    const val MIN_TIMEOUT_MS = 15_000L
    const val DEFAULT_REPORT_DELAY_MS = 5_000L
    const val MAX_PRESSURE_RAW = 800.0
    const val DEFAULT_MQTT_PORT = 1883
    const val MDNS_HOST = "easysmart.local"
    const val UPLINK_SUBSCRIPTION_FILTER = "/dpub/+"
    const val SAFE_VOLTAGE_LIMIT_V = 36
    const val UNSAFE_VOLTAGE_LIMIT_V = 75
    const val MIN_STIM_DELAY_MS = 20
    const val MAX_STIM_DELAY_MS = 1000

    val fields: List<DeviceFieldSpec> = listOf(
        DeviceFieldSpec("ver", "固件版本", FieldType.STRING, false, "", "如 v1.1.41", "完整上报附带的固件版本字符串", FieldGroup.BASIC),
        DeviceFieldSpec("device_type", "设备型号", FieldType.STRING, false, "", "CUNZHI01", "只读设备型号标识", FieldGroup.BASIC),
        DeviceFieldSpec("battery", "电池电量", FieldType.INT, false, "%", "运行时更新", "只读，电池百分比", FieldGroup.BASIC),
        DeviceFieldSpec("report_delay_ms", "遥测上报周期", FieldType.INT, true, "ms", "5000 (100–10000)", "周期遥测间隔，影响超时阈值判定", FieldGroup.BASIC),
        DeviceFieldSpec("sleep_time", "休眠超时", FieldType.INT, true, "s", "7200", "休眠前允许无有效入站消息的时间", FieldGroup.BASIC),

        DeviceFieldSpec("pressure", "第一路压力", FieldType.FLOAT, false, "raw", "0 (范围 0–800)", "只读，第一路压力原始读数，0 为有效读数", FieldGroup.PRESSURE),
        DeviceFieldSpec("pressure1", "第二路压力", FieldType.FLOAT, false, "raw", "0 (范围 0–800)", "只读，第二路压力原始读数，0 为有效读数", FieldGroup.PRESSURE),
        DeviceFieldSpec("game_p1_thresh", "第一路阈值", FieldType.FLOAT, true, "raw", "0", "第一路压力阈值，与原始压力同量纲", FieldGroup.PRESSURE),
        DeviceFieldSpec("game_p2_thresh", "第二路阈值", FieldType.FLOAT, true, "raw", "0", "第二路压力阈值，与原始压力同量纲", FieldGroup.PRESSURE),

        DeviceFieldSpec("power", "电机状态", FieldType.INT, true, "", "0", "电机状态字段", FieldGroup.MOTOR_STIM),
        DeviceFieldSpec("voltage", "电刺激电压", FieldType.INT, true, "V", "上限 36V / 75V", "电刺激电压（V），上限由 safe 控制：safe=1 上限 36V，safe=0 上限 75V", FieldGroup.MOTOR_STIM),
        DeviceFieldSpec("shock", "电刺激开关", FieldType.INT, true, "", "0=关 / 1=开", "电刺激触发开关，仅支持设置 0（关闭）或 1（开启）", FieldGroup.MOTOR_STIM),
        DeviceFieldSpec("delay", "电刺激延时", FieldType.INT, true, "ms", "20–1000 ms", "电刺激时间/延时（毫秒），允许范围 20–1000 ms", FieldGroup.MOTOR_STIM),
        DeviceFieldSpec("safe", "电压上限保护", FieldType.INT, true, "", "1=36V / 0=75V", "调整电刺激电压上限：safe=1 上限 36V，safe=0 上限 75V（可从 NVS 恢复）", FieldGroup.MOTOR_STIM),

        DeviceFieldSpec("game_mode", "内置模式", FieldType.INT, true, "", "0", "设备内置模式编号", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_cz_count", "计数", FieldType.INT, true, "次", "0", "计数，随周期遥测上报", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_duration", "模式总时长", FieldType.INT, true, "s", "0", "模式总时长（秒）", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_fly_dur", "阶段时长", FieldType.INT, true, "s", "60", "模式阶段时长（秒）", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_e_vol", "模式电刺激强度", FieldType.INT, true, "", "—", "模式内电刺激相关字段", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_e_dur", "模式电刺激时长", FieldType.INT, true, "", "—", "模式内电刺激相关时间字段", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_m_dur", "模式电机时长", FieldType.INT, true, "ms", "0", "模式内电机时长（毫秒）", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_m_power", "模式电机强度", FieldType.INT, true, "", "0", "模式内电机强度字段", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_m_step", "电机强度步进", FieldType.INT, true, "", "0", "模式内电机强度步进", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_cooldown", "模式冷却时长", FieldType.INT, true, "ms", "0", "模式冷却时长（毫秒）", FieldGroup.GAME_TIMING),
        DeviceFieldSpec("game_kegel_t", "模式计时参数", FieldType.INT, true, "ms", "0", "模式内计时参数（毫秒）", FieldGroup.GAME_TIMING),
    )

    val fieldByKey: Map<String, DeviceFieldSpec> = fields.associateBy { it.key }
    val periodicUpdateKeys: Set<String> = setOf("pressure", "pressure1", "battery", "game_cz_count")
}

data class PressureSample(
    val at: Long,
    val first: Double?,
    val second: Double?
)

data class DeviceRecord(
    val mac: String,
    val fields: Map<String, Any>,
    val lastSeen: Long,
    val lastBrokerReceivedAt: Long,
    val reportCount: Int,
    val updateCount: Int,
    val history: List<PressureSample>,
    val unknownFields: Map<String, String> = emptyMap()
)

data class DeviceSnapshot(
    val mac: String,
    val fields: Map<String, Any>,
    val lastSeen: Long,
    val lastBrokerReceivedAt: Long,
    val online: Boolean,
    val timeoutMs: Long,
    val ageMs: Long,
    val lastLatencyMs: Long,
    val reportCount: Int,
    val updateCount: Int,
    val history: List<PressureSample>,
    val unknownFields: Map<String, String>
) {
    val deviceType: String? get() = fields["device_type"] as? String
    val firmwareVersion: String? get() = fields["ver"] as? String
    val pressure1: Double? get() = (fields["pressure"] as? Number)?.toDouble()
    val pressure2: Double? get() = (fields["pressure1"] as? Number)?.toDouble()
    val battery: Int? get() = (fields["battery"] as? Number)?.toInt()
    val czCount: Int? get() = (fields["game_cz_count"] as? Number)?.toInt()
    val reportDelayMs: Int? get() = (fields["report_delay_ms"] as? Number)?.toInt()
    val p1Threshold: Double? get() = (fields["game_p1_thresh"] as? Number)?.toDouble()
    val p2Threshold: Double? get() = (fields["game_p2_thresh"] as? Number)?.toDouble()
    val gameMode: Int? get() = (fields["game_mode"] as? Number)?.toInt()
    val sleepTime: Int? get() = (fields["sleep_time"] as? Number)?.toInt()
    val safeMode: Int? get() = (fields["safe"] as? Number)?.toInt()
    val maxVoltageLimitV: Int
        get() = if (safeMode == 0) CunzhiProtocolSchema.UNSAFE_VOLTAGE_LIMIT_V else CunzhiProtocolSchema.SAFE_VOLTAGE_LIMIT_V
}

enum class DownlinkFormat(val apiName: String, val label: String) {
    KEY_VALUE("key_value", "标准 key/value"),
    FLAT("flat", "平铺字段")
}

enum class LogCategory(val label: String) {
    TELEMETRY("遥测"),
    DOWNLINK("下发"),
    SYSTEM("系统"),
    MDNS("mDNS"),
    IGNORED("忽略/异常")
}

data class ProtocolLogEntry(
    val id: Long,
    val timestampMs: Long,
    val category: LogCategory,
    val title: String,
    val detail: String,
    val latencyMs: Long? = null
)

data class DownlinkResult(
    val success: Boolean,
    val topic: String,
    val payload: String,
    val message: String,
    val msgId: Int
)

data class MonitorConfig(
    val mode: MonitorMode = MonitorMode.EMBEDDED,
    val embeddedPort: Int = CunzhiProtocolSchema.DEFAULT_MQTT_PORT,
    val mdnsEnabled: Boolean = true,
    val mdnsIpOverride: String = "",
    val externalBrokerUrl: String = "mqtt://192.168.1.100:1883",
    val externalUsername: String = "",
    val externalPassword: String = "",
    val keepScreenOn: Boolean = true,
    val keepAliveLocksEnabled: Boolean = true,
    val autoRecoverNetwork: Boolean = true,
    val downlinkFormat: DownlinkFormat = DownlinkFormat.KEY_VALUE
)

data class RuntimeStatus(
    val mode: MonitorMode = MonitorMode.EMBEDDED,
    val status: ConnectionStatus = ConnectionStatus.STOPPED,
    val mqttReady: Boolean = false,
    val brokerPort: Int? = null,
    val discoveryIp: String? = null,
    val mdnsActive: Boolean = false,
    val mdnsResponses: Int = 0,
    val activeEndpoint: String = "",
    val connectedClients: Int = 0,
    val reconnectAttempts: Int = 0,
    val errorMessage: String? = null,
    val detectedLocalIps: List<String> = emptyList(),
    val wakeLockActive: Boolean = false,
    val wifiLockActive: Boolean = false,
    val networkRecoveries: Int = 0
)
