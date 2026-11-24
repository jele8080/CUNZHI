package com.example.telemetry

import com.example.model.CunzhiProtocolSchema
import com.example.model.DeviceRecord
import com.example.model.DeviceSnapshot
import com.example.model.FieldType
import com.example.model.LogCategory
import com.example.model.PressureSample
import com.example.model.ProtocolLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.floor
import kotlin.math.max

sealed class IngestResult {
    data class Accepted(
        val mac: String,
        val kind: String,
        val updatedKeys: Set<String>,
        val addedHistorySample: Boolean
    ) : IngestResult()

    data class Ignored(val reason: String) : IngestResult()
}

/**
 * Pure protocol processor mirroring telemetry.js from the PC specification.
 * Validates topic MAC, JSON structure, strict field types, merges full/periodic/single updates,
 * maintains up to 180 periodic pressure samples per device, and evaluates online timeout.
 */
class TelemetryEngine(
    private val maxLogs: Int = 200
) {
    private val lock = Any()
    private val logSeq = AtomicLong(0L)
    private val devicesMap = LinkedHashMap<String, DeviceRecord>()
    private val logBuffer = ArrayDeque<ProtocolLogEntry>(maxLogs)

    private val _records = MutableStateFlow<List<DeviceRecord>>(emptyList())
    val records: StateFlow<List<DeviceRecord>> = _records.asStateFlow()

    private val _logs = MutableStateFlow<List<ProtocolLogEntry>>(emptyList())
    val logs: StateFlow<List<ProtocolLogEntry>> = _logs.asStateFlow()

    fun parseMacFromTopic(topic: String): String? {
        val match = UPLINK_TOPIC_REGEX.matchEntire(topic.trim()) ?: return null
        return match.groupValues[1].lowercase(Locale.US)
    }

    fun ingest(
        topic: String,
        payload: String,
        brokerReceivedAtMs: Long = System.currentTimeMillis(),
        appliedAtMs: Long = System.currentTimeMillis()
    ): IngestResult {
        val mac = parseMacFromTopic(topic)
        if (mac == null) {
            recordLog(
                LogCategory.IGNORED,
                "主题格式无效",
                "已忽略主题: $topic",
                timestampMs = appliedAtMs
            )
            return IngestResult.Ignored("Invalid topic: $topic")
        }

        val json = SimpleJsonParser.parseObject(payload)
        if (json == null) {
            recordLog(
                LogCategory.IGNORED,
                "无效 JSON ($mac)",
                payload.take(120),
                timestampMs = appliedAtMs
            )
            return IngestResult.Ignored("Invalid JSON")
        }

        val method = json["method"] as? String
        if (method.isNullOrBlank()) {
            recordLog(
                LogCategory.IGNORED,
                "缺少 method ($mac)",
                payload.take(120),
                timestampMs = appliedAtMs
            )
            return IngestResult.Ignored("Missing method")
        }

        return when (method) {
            "report" -> handleFullReport(mac, json, brokerReceivedAtMs, appliedAtMs)
            "update" -> handleUpdate(mac, json, brokerReceivedAtMs, appliedAtMs)
            else -> {
                recordLog(
                    LogCategory.IGNORED,
                    "未知 method=$method ($mac)",
                    payload.take(120),
                    timestampMs = appliedAtMs
                )
                IngestResult.Ignored("Unknown method: $method")
            }
        }
    }

    private fun handleFullReport(
        mac: String,
        json: Map<String, Any?>,
        brokerReceivedAtMs: Long,
        appliedAtMs: Long
    ): IngestResult {
        val validated = LinkedHashMap<String, Any>()
        val unknowns = LinkedHashMap<String, String>()

        for ((key, rawValue) in json) {
            if (key == "method" || key == "msg_id") continue
            val spec = CunzhiProtocolSchema.fieldByKey[key]
            if (spec == null) {
                if (rawValue != null) {
                    unknowns[key] = rawValue.toString()
                }
                continue
            }
            val parsed = parseTypedValue(rawValue, spec.type)
            if (parsed != null) {
                validated[key] = parsed
            }
        }

        if (validated.isEmpty()) {
            recordLog(
                LogCategory.IGNORED,
                "空 report 帧 ($mac)",
                "未包含可识别的有效属性字段",
                timestampMs = appliedAtMs
            )
            return IngestResult.Ignored("No valid report fields")
        }

        val latency = (appliedAtMs - brokerReceivedAtMs).coerceAtLeast(0L)
        synchronized(lock) {
            val existing = devicesMap[mac]
            val mergedFields = LinkedHashMap<String, Any>()
            if (existing != null) mergedFields.putAll(existing.fields)
            mergedFields.putAll(validated)

            val mergedUnknowns = LinkedHashMap<String, String>()
            if (existing != null) mergedUnknowns.putAll(existing.unknownFields)
            mergedUnknowns.putAll(unknowns)

            // Per spec: full report does NOT append to pressure history curve
            devicesMap[mac] = DeviceRecord(
                mac = mac,
                fields = mergedFields,
                lastSeen = appliedAtMs,
                lastBrokerReceivedAt = brokerReceivedAtMs,
                reportCount = (existing?.reportCount ?: 0) + 1,
                updateCount = existing?.updateCount ?: 0,
                history = existing?.history ?: emptyList(),
                unknownFields = mergedUnknowns
            )
            _records.value = devicesMap.values.toList()
        }

        val verText = validated["ver"]?.toString()?.let { " · $it" }.orEmpty()
        recordLog(
            LogCategory.TELEMETRY,
            "完整上报 report ($mac$verText)",
            "已合并 ${validated.size} 项属性" + if (unknowns.isNotEmpty()) "，忽略未知字段 ${unknowns.keys.joinToString()}" else "",
            timestampMs = appliedAtMs,
            latencyMs = latency
        )

        return IngestResult.Accepted(
            mac = mac,
            kind = "report",
            updatedKeys = validated.keys,
            addedHistorySample = false
        )
    }

    private fun handleUpdate(
        mac: String,
        json: Map<String, Any?>,
        brokerReceivedAtMs: Long,
        appliedAtMs: Long
    ): IngestResult {
        if (json.containsKey("key") && json.containsKey("value")) {
            return handleSingleItemReply(mac, json, brokerReceivedAtMs, appliedAtMs)
        }

        val validated = LinkedHashMap<String, Any>()
        var sampleFirst: Double? = null
        var sampleSecond: Double? = null
        var hasPressureReading = false

        for (key in CunzhiProtocolSchema.periodicUpdateKeys) {
            if (!json.containsKey(key)) continue
            val spec = CunzhiProtocolSchema.fieldByKey[key] ?: continue
            val parsed = parseTypedValue(json[key], spec.type) ?: continue
            validated[key] = parsed
            if (key == "pressure") {
                sampleFirst = parsed as Double
                hasPressureReading = true
            } else if (key == "pressure1") {
                sampleSecond = parsed as Double
                hasPressureReading = true
            }
        }

        if (validated.isEmpty()) {
            recordLog(
                LogCategory.IGNORED,
                "无效 update 帧 ($mac)",
                "无有效周期字段: ${json.keys.joinToString()}",
                timestampMs = appliedAtMs
            )
            return IngestResult.Ignored("No valid periodic update fields")
        }

        val latency = (appliedAtMs - brokerReceivedAtMs).coerceAtLeast(0L)
        synchronized(lock) {
            val existing = devicesMap[mac]
            val mergedFields = LinkedHashMap<String, Any>()
            if (existing != null) mergedFields.putAll(existing.fields)
            mergedFields.putAll(validated)

            val updatedHistory = if (hasPressureReading) {
                val base = existing?.history ?: emptyList()
                val next = ArrayList<PressureSample>(minOf(base.size + 1, CunzhiProtocolSchema.MAX_HISTORY_POINTS))
                val dropCount = (base.size + 1 - CunzhiProtocolSchema.MAX_HISTORY_POINTS).coerceAtLeast(0)
                if (dropCount < base.size) {
                    next.addAll(base.subList(dropCount, base.size))
                }
                next.add(PressureSample(at = appliedAtMs, first = sampleFirst, second = sampleSecond))
                next
            } else {
                existing?.history ?: emptyList()
            }

            devicesMap[mac] = DeviceRecord(
                mac = mac,
                fields = mergedFields,
                lastSeen = appliedAtMs,
                lastBrokerReceivedAt = brokerReceivedAtMs,
                reportCount = existing?.reportCount ?: 0,
                updateCount = (existing?.updateCount ?: 0) + 1,
                history = updatedHistory,
                unknownFields = existing?.unknownFields ?: emptyMap()
            )
            _records.value = devicesMap.values.toList()
        }

        val summary = buildString {
            sampleFirst?.let { append("P1=${formatNum(it)} ") }
            sampleSecond?.let { append("P2=${formatNum(it)} ") }
            validated["battery"]?.let { append("电量=$it% ") }
            validated["game_cz_count"]?.let { append("计数=$it") }
        }.trim()

        recordLog(
            LogCategory.TELEMETRY,
            "周期遥测 update ($mac)",
            summary,
            timestampMs = appliedAtMs,
            latencyMs = latency
        )

        return IngestResult.Accepted(
            mac = mac,
            kind = "update_periodic",
            updatedKeys = validated.keys,
            addedHistorySample = hasPressureReading
        )
    }

    private fun handleSingleItemReply(
        mac: String,
        json: Map<String, Any?>,
        brokerReceivedAtMs: Long,
        appliedAtMs: Long
    ): IngestResult {
        val key = json["key"] as? String
        if (key.isNullOrBlank()) {
            recordLog(LogCategory.IGNORED, "单项回复 key 无效 ($mac)", json.toString().take(100), appliedAtMs)
            return IngestResult.Ignored("Invalid key in single-item update")
        }
        val spec = CunzhiProtocolSchema.fieldByKey[key]
        if (spec == null) {
            recordLog(LogCategory.IGNORED, "单项回复未知字段 ($mac)", "key=$key", appliedAtMs)
            return IngestResult.Ignored("Unknown field key: $key")
        }
        val parsed = parseTypedValue(json["value"], spec.type)
        if (parsed == null) {
            recordLog(LogCategory.IGNORED, "单项回复类型不匹配 ($mac)", "key=$key, value=${json["value"]}", appliedAtMs)
            return IngestResult.Ignored("Invalid value type for $key")
        }

        val latency = (appliedAtMs - brokerReceivedAtMs).coerceAtLeast(0L)
        synchronized(lock) {
            val existing = devicesMap[mac]
            val mergedFields = LinkedHashMap<String, Any>()
            if (existing != null) mergedFields.putAll(existing.fields)
            mergedFields[key] = parsed

            devicesMap[mac] = DeviceRecord(
                mac = mac,
                fields = mergedFields,
                lastSeen = appliedAtMs,
                lastBrokerReceivedAt = brokerReceivedAtMs,
                reportCount = existing?.reportCount ?: 0,
                updateCount = (existing?.updateCount ?: 0) + 1,
                history = existing?.history ?: emptyList(),
                unknownFields = existing?.unknownFields ?: emptyMap()
            )
            _records.value = devicesMap.values.toList()
        }

        val msgIdNote = json["msg_id"]?.let { " (msg_id=$it)" }.orEmpty()
        recordLog(
            LogCategory.TELEMETRY,
            "单项回复 ($mac)$msgIdNote",
            "$key = $parsed",
            timestampMs = appliedAtMs,
            latencyMs = latency
        )

        return IngestResult.Accepted(
            mac = mac,
            kind = "update_single",
            updatedKeys = setOf(key),
            addedHistorySample = false
        )
    }

    fun computeSnapshots(nowMs: Long = System.currentTimeMillis()): List<DeviceSnapshot> {
        val currentRecords = synchronized(lock) { devicesMap.values.toList() }
        return currentRecords.map { record ->
            val reportDelayMs = (record.fields["report_delay_ms"] as? Number)?.toLong()
                ?.takeIf { it > 0L }
                ?: CunzhiProtocolSchema.DEFAULT_REPORT_DELAY_MS
            val timeoutMs = max(CunzhiProtocolSchema.MIN_TIMEOUT_MS, reportDelayMs * 3L)
            val ageMs = (nowMs - record.lastSeen).coerceAtLeast(0L)
            val online = ageMs <= timeoutMs
            val latencyMs = (record.lastSeen - record.lastBrokerReceivedAt).coerceAtLeast(0L)
            DeviceSnapshot(
                mac = record.mac,
                fields = record.fields,
                lastSeen = record.lastSeen,
                lastBrokerReceivedAt = record.lastBrokerReceivedAt,
                online = online,
                timeoutMs = timeoutMs,
                ageMs = ageMs,
                lastLatencyMs = latencyMs,
                reportCount = record.reportCount,
                updateCount = record.updateCount,
                history = record.history,
                unknownFields = record.unknownFields
            )
        }
    }

    fun recordLog(
        category: LogCategory,
        title: String,
        detail: String,
        timestampMs: Long = System.currentTimeMillis(),
        latencyMs: Long? = null
    ) {
        synchronized(lock) {
            if (logBuffer.size >= maxLogs) {
                logBuffer.removeLast()
            }
            logBuffer.addFirst(
                ProtocolLogEntry(
                    id = logSeq.incrementAndGet(),
                    timestampMs = timestampMs,
                    category = category,
                    title = title,
                    detail = detail,
                    latencyMs = latencyMs
                )
            )
            _logs.value = logBuffer.toList()
        }
    }

    fun clearHistory(mac: String? = null) {
        synchronized(lock) {
            if (mac == null) {
                for (key in devicesMap.keys.toList()) {
                    val rec = devicesMap[key] ?: continue
                    devicesMap[key] = rec.copy(history = emptyList())
                }
            } else {
                val rec = devicesMap[mac]
                if (rec != null) {
                    devicesMap[mac] = rec.copy(history = emptyList())
                }
            }
            _records.value = devicesMap.values.toList()
        }
    }

    fun clearDevices() {
        synchronized(lock) {
            devicesMap.clear()
            _records.value = emptyList()
        }
    }

    fun clearLogs() {
        synchronized(lock) {
            logBuffer.clear()
            _logs.value = emptyList()
        }
    }

    private fun parseTypedValue(raw: Any?, type: FieldType): Any? {
        if (raw == null) return null
        return when (type) {
            FieldType.STRING -> raw as? String
            FieldType.INT -> when (raw) {
                is Int -> raw
                is Long -> if (raw in Int.MIN_VALUE..Int.MAX_VALUE) raw.toInt() else null
                is Double -> if (raw.isFinite() && floor(raw) == raw && raw >= Int.MIN_VALUE && raw <= Int.MAX_VALUE) raw.toInt() else null
                is Float -> if (raw.isFinite() && floor(raw) == raw) raw.toInt() else null
                else -> null
            }
            FieldType.FLOAT -> when (raw) {
                is Number -> {
                    val d = raw.toDouble()
                    if (d.isFinite()) d else null
                }
                else -> null
            }
        }
    }

    private fun formatNum(value: Double): String {
        return if (floor(value) == value) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
    }

    companion object {
        private val UPLINK_TOPIC_REGEX = Regex("^/dpub/([0-9a-fA-F]{12})$")
    }
}
