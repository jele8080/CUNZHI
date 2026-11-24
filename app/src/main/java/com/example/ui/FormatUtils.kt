package com.example.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

object FormatUtils {
    private val timeFormatMs = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val timeFormatShort = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun formatPressure(value: Double?): String {
        if (value == null) return "—"
        return String.format(Locale.US, "%.1f", value)
    }

    fun formatFieldValue(value: Any?, unit: String = ""): String {
        if (value == null) return "—"
        val formatted = when (value) {
            is Double -> if (floor(value) == value) String.format(Locale.US, "%.1f", value) else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
            is Float -> String.format(Locale.US, "%.1f", value)
            else -> value.toString()
        }
        return if (unit.isNotEmpty() && unit != "raw") "$formatted $unit" else formatted
    }

    fun formatClockMs(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        return synchronized(timeFormatMs) {
            timeFormatMs.format(Date(epochMs))
        }
    }

    fun formatClockShort(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        return synchronized(timeFormatShort) {
            timeFormatShort.format(Date(epochMs))
        }
    }

    fun formatAge(ageMs: Long): String {
        val clamped = ageMs.coerceAtLeast(0L)
        return when {
            clamped < 1_000L -> "刚刚"
            clamped < 60_000L -> String.format(Locale.US, "%.1fs 前", clamped / 1000.0)
            clamped < 3_600_000L -> "${clamped / 60_000L}m ${(clamped % 60_000L) / 1000L}s 前"
            else -> "${clamped / 3_600_000L}h 前"
        }
    }

    fun formatMacPretty(mac: String): String {
        val clean = mac.lowercase()
        if (clean.length != 12) return clean
        return clean.chunked(2).joinToString(":")
    }
}
