package com.example.telemetry

import com.example.model.DownlinkFormat
import java.util.Locale
import kotlin.math.floor

/**
 * Pure-Kotlin builder for CUNZHI01 downlink MQTT commands sent to /drecv/<mac>.
 */
object DownlinkCommandBuilder {

    fun downlinkTopic(mac: String): String = "/drecv/${mac.trim().lowercase(Locale.US)}"

    fun buildGetCommand(key: String, msgId: Int): String {
        return """{"method":"get","key":"${escapeJson(key)}","msg_id":$msgId}"""
    }

    fun buildSetCommand(
        key: String,
        value: Any,
        msgId: Int,
        format: DownlinkFormat = DownlinkFormat.KEY_VALUE
    ): String {
        val serializedVal = formatJsonValue(value)
        return when (format) {
            DownlinkFormat.KEY_VALUE ->
                """{"method":"set","key":"${escapeJson(key)}","value":$serializedVal,"msg_id":$msgId}"""
            DownlinkFormat.FLAT ->
                """{"method":"set","${escapeJson(key)}":$serializedVal,"msg_id":$msgId}"""
        }
    }

    fun buildBatchFlatSetCommand(
        values: Map<String, Any>,
        msgId: Int
    ): String {
        val pairs = values.entries.joinToString(",") { (k, v) ->
            """"${escapeJson(k)}":${formatJsonValue(v)}"""
        }
        return """{"method":"set",$pairs,"msg_id":$msgId}"""
    }

    private fun formatJsonValue(value: Any): String {
        return when (value) {
            is Int, is Long -> value.toString()
            is Double -> {
                if (floor(value) == value && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
                    value.toLong().toString()
                } else {
                    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
                }
            }
            is Float -> formatJsonValue(value.toDouble())
            is Boolean -> value.toString()
            else -> """"${escapeJson(value.toString())}""""
        }
    }

    private fun escapeJson(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
