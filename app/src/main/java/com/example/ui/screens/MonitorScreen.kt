package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionStatus
import com.example.model.CunzhiProtocolSchema
import com.example.model.DeviceSnapshot
import com.example.model.MonitorMode
import com.example.model.RuntimeStatus
import com.example.ui.FormatUtils
import com.example.ui.MonitorUiState
import com.example.ui.components.PressureChartCard
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun MonitorScreen(
    uiState: MonitorUiState,
    onSelectDevice: (String) -> Unit,
    onToggleService: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onToggleChartAutoScale: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenProperties: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "runtime_header") {
                RuntimeStatusBanner(
                    runtime = uiState.runtime,
                    onlineCount = uiState.onlineCount,
                    totalCount = uiState.devices.size,
                    keepScreenOn = uiState.config.keepScreenOn,
                    onToggleService = onToggleService,
                    onToggleKeepScreenOn = onToggleKeepScreenOn,
                    onOpenConnection = onOpenConnection
                )
            }

            if (!uiState.backgroundHealth.allCoreChecksPassed) {
                item(key = "xiaomi_health_hint") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenConnection)
                            .testTag("monitor_xiaomi_hint_banner"),
                        shape = RoundedCornerShape(14.dp),
                        color = LocalTelemetryColors.current.warning.copy(alpha = 0.13f),
                        border = BorderStroke(1.dp, LocalTelemetryColors.current.warning.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = "后台保活提示",
                                    tint = LocalTelemetryColors.current.warning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "检测到省电策略或通知限制，息屏后可能断连，点此前往小米保活优化",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "前往优化",
                                tint = LocalTelemetryColors.current.warning,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.devices.isNotEmpty()) {
                item(key = "device_selector") {
                    DeviceSelectorStrip(
                        devices = uiState.devices,
                        selectedMac = uiState.selectedDevice?.mac,
                        onSelectDevice = onSelectDevice
                    )
                }
            }

            val selected = uiState.selectedDevice
            if (selected == null) {
                item(key = "empty_waiting") {
                    WaitingForDeviceCard(
                        runtime = uiState.runtime,
                        onOpenConnection = onOpenConnection,
                        onToggleService = onToggleService
                    )
                }
            } else {
                item(key = "device_hero") {
                    SelectedDeviceHeaderCard(device = selected)
                }

                item(key = "pressure_bento") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PressureGaugeCard(
                            title = "第一路压力",
                            fieldKey = "pressure",
                            value = selected.pressure1,
                            threshold = selected.p1Threshold,
                            accentColor = LocalTelemetryColors.current.channel1,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pressure1_metric_card")
                        )
                        PressureGaugeCard(
                            title = "第二路压力",
                            fieldKey = "pressure1",
                            value = selected.pressure2,
                            threshold = selected.p2Threshold,
                            accentColor = LocalTelemetryColors.current.channel2,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pressure2_metric_card")
                        )
                    }
                }

                item(key = "secondary_bento") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BatteryMetricCard(
                            battery = selected.battery,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("battery_metric_card")
                        )
                        CountAndHeartbeatCard(
                            count = selected.czCount,
                            ageMs = selected.ageMs,
                            timeoutMs = selected.timeoutMs,
                            lastSeen = selected.lastSeen,
                            online = selected.online,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("count_metric_card")
                        )
                    }
                }

                item(key = "pressure_chart") {
                    PressureChartCard(
                        history = selected.history,
                        p1Threshold = selected.p1Threshold,
                        p2Threshold = selected.p2Threshold,
                        autoScale = uiState.autoScaleChart,
                        onToggleAutoScale = onToggleChartAutoScale,
                        onClearHistory = onClearHistory
                    )
                }

                item(key = "quick_config_summary") {
                    QuickConfigSummaryCard(
                        device = selected,
                        onOpenProperties = onOpenProperties
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun RuntimeStatusBanner(
    runtime: RuntimeStatus,
    onlineCount: Int,
    totalCount: Int,
    keepScreenOn: Boolean,
    onToggleService: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onOpenConnection: () -> Unit
) {
    val telemetryColors = LocalTelemetryColors.current
    val colorScheme = MaterialTheme.colorScheme
    val isActive = runtime.status == ConnectionStatus.RUNNING ||
        runtime.status == ConnectionStatus.STARTING ||
        runtime.status == ConnectionStatus.RECONNECTING

    val statusDotColor = when (runtime.status) {
        ConnectionStatus.RUNNING -> telemetryColors.online
        ConnectionStatus.STARTING, ConnectionStatus.RECONNECTING -> telemetryColors.warning
        ConnectionStatus.ERROR -> telemetryColors.offline
        ConnectionStatus.STOPPED -> colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("runtime_status_banner"),
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colorScheme.primaryContainer.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "寸止 MQTT 监看",
                                style = MaterialTheme.typography.titleLarge,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "${runtime.mode.label} · ${runtime.status.label} · 在线 $onlineCount/$totalCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleKeepScreenOn,
                            modifier = Modifier.testTag("keep_screen_on_toggle")
                        ) {
                            Icon(
                                imageVector = if (keepScreenOn) Icons.Rounded.BrightnessHigh else Icons.Rounded.BrightnessMedium,
                                contentDescription = if (keepScreenOn) "屏幕常亮已开启" else "开启屏幕常亮",
                                tint = if (keepScreenOn) telemetryColors.warning else colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = onToggleService,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isActive) {
                                    telemetryColors.offline.copy(alpha = 0.16f)
                                } else {
                                    telemetryColors.online.copy(alpha = 0.18f)
                                },
                                contentColor = if (isActive) telemetryColors.offline else telemetryColors.online
                            ),
                            modifier = Modifier.testTag("service_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isActive) "停止" else "启动",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Endpoint & Discovery Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .clickable(onClick = onOpenConnection)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("endpoint_summary_row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (runtime.mode == MonitorMode.EMBEDDED) Icons.Rounded.Router else Icons.Rounded.SettingsEthernet,
                            contentDescription = "网络端点",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val endpointText = when (runtime.mode) {
                            MonitorMode.EMBEDDED -> {
                                val ip = runtime.discoveryIp ?: runtime.detectedLocalIps.firstOrNull() ?: "未连接 Wi-Fi"
                                val port = runtime.brokerPort ?: CunzhiProtocolSchema.DEFAULT_MQTT_PORT
                                if (runtime.mdnsActive) "easysmart.local -> $ip:$port" else "$ip:$port"
                            }
                            MonitorMode.EXTERNAL -> runtime.activeEndpoint.ifBlank { "未配置 Broker 地址" }
                        }
                        Text(
                            text = endpointText,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (runtime.mode == MonitorMode.EMBEDDED) {
                            "连接 ${runtime.connectedClients}"
                        } else {
                            "配置"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary
                    )
                }

                if (!runtime.errorMessage.isNullOrBlank()) {
                    Text(
                        text = runtime.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = telemetryColors.offline
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceSelectorStrip(
    devices: List<DeviceSnapshot>,
    selectedMac: String?,
    onSelectDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val colorScheme = MaterialTheme.colorScheme

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_selector_strip"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(devices, key = { it.mac }) { device ->
            val isSelected = device.mac == selectedMac
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) colorScheme.primaryContainer else colorScheme.surface,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectDevice(device.mac) }
                    .testTag("device_chip_${device.mac}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (device.online) telemetryColors.online else telemetryColors.offline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = device.mac,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                        )
                        Text(
                            text = listOfNotNull(
                                device.deviceType ?: "CUNZHI01",
                                device.firmwareVersion
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDeviceHeaderCard(device: DeviceSnapshot) {
    val telemetryColors = LocalTelemetryColors.current
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.deviceType ?: "CUNZHI01",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (device.online) {
                            telemetryColors.online.copy(alpha = 0.16f)
                        } else {
                            telemetryColors.offline.copy(alpha = 0.16f)
                        }
                    ) {
                        Text(
                            text = if (device.online) "在线" else "已超时",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (device.online) telemetryColors.online else telemetryColors.offline,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "MAC ${FormatUtils.formatMacPretty(device.mac)} · 固件 ${device.firmwareVersion ?: "未知"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FormatUtils.formatAge(device.ageMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (device.online) colorScheme.onSurface else telemetryColors.offline
                )
                Text(
                    text = "超时阈值 ${device.timeoutMs / 1000}s · +${device.lastLatencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PressureGaugeCard(
    title: String,
    fieldKey: String,
    value: Double?,
    threshold: Double?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val ratio = ((value ?: 0.0) / CunzhiProtocolSchema.MAX_PRESSURE_RAW).coerceIn(0.0, 1.0).toFloat()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = fieldKey,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = FormatUtils.formatPressure(value),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (value != null) colorScheme.onSurface else colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "raw",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linear raw gauge bar (0..800)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colorScheme.surfaceVariant)
            ) {
                if (value != null && ratio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (threshold != null && threshold > 0.0) {
                    "阈值 ${FormatUtils.formatPressure(threshold)} · 范围 0–800"
                } else {
                    "原始读数 · 范围 0–800"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryMetricCard(
    battery: Int?,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val colorScheme = MaterialTheme.colorScheme
    val tint = when {
        battery == null -> colorScheme.onSurfaceVariant
        battery <= 20 -> telemetryColors.offline
        battery <= 45 -> telemetryColors.warning
        else -> telemetryColors.online
    }
    val icon: ImageVector = when {
        battery == null -> Icons.Rounded.BatteryStd
        battery <= 20 -> Icons.Rounded.BatteryAlert
        battery >= 90 -> Icons.Rounded.BatteryFull
        else -> Icons.Rounded.BatteryChargingFull
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "电池电量",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = "电池电量",
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (battery != null) "$battery%" else "—",
                style = MaterialTheme.typography.headlineMedium,
                color = if (battery != null) tint else colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "battery · 运行时更新",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CountAndHeartbeatCard(
    count: Int?,
    ageMs: Long,
    timeoutMs: Long,
    lastSeen: Long,
    online: Boolean,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "计数 / 上报",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Rounded.Tag,
                    contentDescription = "计数",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = count?.toString() ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = if (online) colorScheme.onSurfaceVariant else telemetryColors.offline,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${FormatUtils.formatClockShort(lastSeen)} (${FormatUtils.formatAge(ageMs)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (online) colorScheme.onSurfaceVariant else telemetryColors.offline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickConfigSummaryCard(
    device: DeviceSnapshot,
    onOpenProperties: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenProperties)
            .testTag("quick_properties_card"),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "设备配置摘要",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "全部 ${CunzhiProtocolSchema.fields.size} 项属性",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "查看全部属性",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniKeyValuePill(
                    label = "上报周期",
                    value = device.reportDelayMs?.let { "${it} ms" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MiniKeyValuePill(
                    label = "内置模式",
                    value = device.gameMode?.toString() ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MiniKeyValuePill(
                    label = "休眠时间",
                    value = device.sleepTime?.let { "${it} s" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "累计收到 report ${device.reportCount} 帧 · update ${device.updateCount} 帧 · 上行主题 /dpub/${device.mac}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniKeyValuePill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = colorScheme.onSurface
        )
    }
}

@Composable
private fun WaitingForDeviceCard(
    runtime: RuntimeStatus,
    onOpenConnection: () -> Unit,
    onToggleService: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("waiting_for_device_card"),
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primaryContainer.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (runtime.mode == MonitorMode.EMBEDDED) Icons.Rounded.Sensors else Icons.Rounded.Wifi,
                    contentDescription = "等待设备遥测",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when (runtime.status) {
                    ConnectionStatus.STOPPED -> "监看服务未启动"
                    ConnectionStatus.ERROR -> "连接异常，请检查网络配置"
                    else -> "等待 CUNZHI01 上报遥测..."
                },
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            val guideText = if (runtime.mode == MonitorMode.EMBEDDED) {
                val ip = runtime.discoveryIp ?: runtime.detectedLocalIps.firstOrNull() ?: "请先连接 Wi-Fi 或开启热点"
                val port = runtime.brokerPort ?: CunzhiProtocolSchema.DEFAULT_MQTT_PORT
                "手机内置 MQTT Broker 监听 TCP $port，并通过 UDP 5353 回应 easysmart.local -> $ip。\n" +
                    "请确保 CUNZHI01 与手机在同一局域网（或连入手机热点），必要时让设备重连 Wi-Fi 以重新发现 Broker。"
            } else {
                "正在订阅外部 Broker (${runtime.activeEndpoint}) 的 /dpub/+（QoS 1）。\n" +
                    "由于设备上报不保留 (retain=false)，订阅成功后需等待下一条周期上报或 10 秒心跳到达。"
            }

            Text(
                text = guideText,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick protocol spec badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniKeyValuePill(
                    label = "上行主题",
                    value = "/dpub/<mac>",
                    modifier = Modifier.weight(1f)
                )
                MiniKeyValuePill(
                    label = "发现域名",
                    value = "easysmart.local",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (runtime.status == ConnectionStatus.STOPPED || runtime.status == ConnectionStatus.ERROR) {
                    Button(
                        onClick = onToggleService,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("empty_start_button")
                    ) {
                        Text("启动监看服务")
                    }
                }
                FilledTonalButton(
                    onClick = onOpenConnection,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_open_connection_button")
                ) {
                    Text(
                        text = if (runtime.mode == MonitorMode.EMBEDDED) "切换模式 / 查看日志" else "修改 Broker 地址",
                        color = if (runtime.status == ConnectionStatus.ERROR) telemetryColors.offline else colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
