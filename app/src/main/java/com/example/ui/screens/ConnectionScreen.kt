package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionStatus
import com.example.model.CunzhiProtocolSchema
import com.example.model.LogCategory
import com.example.model.MonitorConfig
import com.example.model.MonitorMode
import com.example.model.ProtocolLogEntry
import com.example.ui.FormatUtils
import com.example.ui.MonitorUiState
import com.example.ui.components.XiaomiKeepAliveCard
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionScreen(
    uiState: MonitorUiState,
    onSaveAndRestart: (MonitorConfig) -> Unit,
    onToggleService: () -> Unit,
    onRefreshIps: () -> Unit,
    onToggleKeepAliveLocks: (Boolean) -> Unit,
    onToggleAutoRecoverNetwork: (Boolean) -> Unit,
    onSelectLogFilter: (LogCategory?) -> Unit,
    onClearLogs: () -> Unit,
    onClearDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current

    var draftMode by remember { mutableStateOf(uiState.config.mode) }
    var draftPort by remember { mutableStateOf(uiState.config.embeddedPort.toString()) }
    var draftMdnsEnabled by remember { mutableStateOf(uiState.config.mdnsEnabled) }
    var draftMdnsIp by remember { mutableStateOf(uiState.config.mdnsIpOverride) }
    var draftExternalUrl by remember { mutableStateOf(uiState.config.externalBrokerUrl) }
    var draftExternalUser by remember { mutableStateOf(uiState.config.externalUsername) }
    var draftExternalPass by remember { mutableStateOf(uiState.config.externalPassword) }

    // Sync draft once saved config loads from DataStore
    LaunchedEffect(uiState.config) {
        draftMode = uiState.config.mode
        draftPort = uiState.config.embeddedPort.toString()
        draftMdnsEnabled = uiState.config.mdnsEnabled
        draftMdnsIp = uiState.config.mdnsIpOverride
        draftExternalUrl = uiState.config.externalBrokerUrl
        draftExternalUser = uiState.config.externalUsername
        draftExternalPass = uiState.config.externalPassword
    }

    val isServiceActive = uiState.runtime.status != ConnectionStatus.STOPPED

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
            item(key = "mode_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "连接模式与 Broker 配置",
                                style = MaterialTheme.typography.titleLarge,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "支持手机内置 MQTT Broker 直接接收，或连接局域网内已有 Broker",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mode_selector_row")
                        ) {
                            MonitorMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = draftMode == mode,
                                    onClick = { draftMode = mode },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = MonitorMode.entries.size
                                    ),
                                    modifier = Modifier.testTag("mode_option_${mode.apiName}")
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }

                        if (draftMode == MonitorMode.EMBEDDED) {
                            EmbeddedModeForm(
                                portText = draftPort,
                                onPortChange = { draftPort = it.filter { ch -> ch.isDigit() }.take(5) },
                                mdnsEnabled = draftMdnsEnabled,
                                onMdnsEnabledChange = { draftMdnsEnabled = it },
                                mdnsIpOverride = draftMdnsIp,
                                onMdnsIpChange = { draftMdnsIp = it },
                                detectedIps = uiState.runtime.detectedLocalIps,
                                mdnsActive = uiState.runtime.mdnsActive,
                                mdnsResponses = uiState.runtime.mdnsResponses,
                                onRefreshIps = onRefreshIps
                            )
                        } else {
                            ExternalModeForm(
                                brokerUrl = draftExternalUrl,
                                onBrokerUrlChange = { draftExternalUrl = it },
                                username = draftExternalUser,
                                onUsernameChange = { draftExternalUser = it },
                                password = draftExternalPass,
                                onPasswordChange = { draftExternalPass = it },
                                reconnectAttempts = uiState.runtime.reconnectAttempts
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val parsedPort = draftPort.toIntOrNull()?.coerceIn(1024, 65535)
                                        ?: CunzhiProtocolSchema.DEFAULT_MQTT_PORT
                                    onSaveAndRestart(
                                        uiState.config.copy(
                                            mode = draftMode,
                                            embeddedPort = parsedPort,
                                            mdnsEnabled = draftMdnsEnabled,
                                            mdnsIpOverride = draftMdnsIp.trim(),
                                            externalBrokerUrl = draftExternalUrl.trim(),
                                            externalUsername = draftExternalUser,
                                            externalPassword = draftExternalPass
                                        )
                                    )
                                    Toast.makeText(context, "配置已保存，正在重启服务", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_restart_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("保存并重启")
                            }

                            if (isServiceActive) {
                                OutlinedButton(
                                    onClick = onToggleService,
                                    modifier = Modifier.testTag("stop_service_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.StopCircle,
                                        contentDescription = null,
                                        tint = telemetryColors.offline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("停止", color = telemetryColors.offline)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "xiaomi_keep_alive") {
                XiaomiKeepAliveCard(
                    health = uiState.backgroundHealth,
                    runtime = uiState.runtime,
                    keepAliveLocksEnabled = uiState.config.keepAliveLocksEnabled,
                    autoRecoverNetwork = uiState.config.autoRecoverNetwork,
                    onToggleKeepAliveLocks = onToggleKeepAliveLocks,
                    onToggleAutoRecoverNetwork = onToggleAutoRecoverNetwork
                )
            }

            item(key = "lan_tips_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Lan,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "手机端局域网联调说明",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "• 内置模式：先关闭局域网内旧电脑 broker 与旧 easysmart.local 宣告，确保手机与 CUNZHI01 处于同一 Wi-Fi（或直接让设备连入本机 Wi-Fi 热点），必要时重启设备 Wi-Fi 以触发 mDNS 发现。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• 已有模式：如果电脑或路由器已运行 MQTT Broker（例如 Mosquitto/Aedes），填入局域网地址（如 mqtt://192.168.10.56:1883），本机仅订阅 /dpub/+（QoS 1），不争用 1883 端口。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (uiState.devices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = onClearDevices,
                                modifier = Modifier.testTag("clear_devices_button")
                            ) {
                                Text("清空已发现设备缓存 (${uiState.devices.size})")
                            }
                        }
                    }
                }
            }

            item(key = "logs_header") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "协议与延迟诊断日志 (${uiState.filteredLogs.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        val dump = uiState.filteredLogs.joinToString("\n") { entry ->
                                            val lat = entry.latencyMs?.let { " [+${it}ms]" }.orEmpty()
                                            "[${FormatUtils.formatClockMs(entry.timestampMs)}] [${entry.category.label}] ${entry.title}$lat - ${entry.detail}"
                                        }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("cunzhi_logs", dump))
                                        Toast.makeText(context, "已复制 ${uiState.filteredLogs.size} 条日志", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("copy_logs_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "复制日志",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onClearLogs,
                                    modifier = Modifier.testTag("clear_logs_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "清空日志",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = uiState.logFilter == null,
                                    onClick = { onSelectLogFilter(null) },
                                    label = { Text("全部") }
                                )
                            }
                            items(LogCategory.entries) { category ->
                                FilterChip(
                                    selected = uiState.logFilter == category,
                                    onClick = { onSelectLogFilter(category) },
                                    label = { Text(category.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (uiState.filteredLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无匹配日志",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val visibleLogs = uiState.filteredLogs.take(50)
                            visibleLogs.forEachIndexed { idx, entry ->
                                LogEntryRow(entry = entry)
                                if (idx < visibleLogs.lastIndex) {
                                    HorizontalDivider(
                                        color = colorScheme.outlineVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.padding(vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmbeddedModeForm(
    portText: String,
    onPortChange: (String) -> Unit,
    mdnsEnabled: Boolean,
    onMdnsEnabledChange: (Boolean) -> Unit,
    mdnsIpOverride: String,
    onMdnsIpChange: (String) -> Unit,
    detectedIps: List<String>,
    mdnsActive: Boolean,
    mdnsResponses: Int,
    onRefreshIps: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Detected local IPv4 chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本机局域网 IPv4（点击自动填入 MDNS_IP）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onRefreshIps,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("refresh_ip_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新本机 IP",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (detectedIps.isEmpty()) {
                Text(
                    text = "未检测到 Wi-Fi 或热点 IPv4，请先连接局域网。",
                    style = MaterialTheme.typography.bodySmall,
                    color = telemetryColors.warning
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    detectedIps.forEach { ip ->
                        val selected = mdnsIpOverride == ip || (mdnsIpOverride.isBlank() && detectedIps.firstOrNull() == ip)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onMdnsIpChange(ip) }
                        ) {
                            Text(
                                text = ip,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = portText,
                onValueChange = onPortChange,
                label = { Text("MQTT 端口") },
                placeholder = { Text("1883") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.42f)
                    .testTag("embedded_port_input")
            )

            OutlinedTextField(
                value = mdnsIpOverride,
                onValueChange = onMdnsIpChange,
                label = { Text("MDNS_IP (可选)") },
                placeholder = { Text(detectedIps.firstOrNull() ?: "自动检测") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.58f)
                    .testTag("mdns_ip_input")
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = if (mdnsActive) telemetryColors.online else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "回应 easysmart.local (UDP 5353)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = if (mdnsActive) "mDNS 运行中 · 已回应 $mdnsResponses 次查询" else "开启后固件可自动发现本机 Broker",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mdnsActive) telemetryColors.online else colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = mdnsEnabled,
                    onCheckedChange = onMdnsEnabledChange,
                    modifier = Modifier.testTag("mdns_switch")
                )
            }
        }
    }
}

@Composable
private fun ExternalModeForm(
    brokerUrl: String,
    onBrokerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    reconnectAttempts: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = brokerUrl,
            onValueChange = onBrokerUrlChange,
            label = { Text("Broker 地址 (MQTT_URL)") },
            placeholder = { Text("mqtt://192.168.10.56:1883 或 easysmart.local") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("external_url_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("用户名 (可选)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("密码 (可选)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "自动订阅 ${CunzhiProtocolSchema.UPLINK_SUBSCRIPTION_FILTER} (QoS 1)，断线自动重连并重新订阅" +
                if (reconnectAttempts > 0) " · 当前重连尝试 #$reconnectAttempts" else "",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogEntryRow(entry: ProtocolLogEntry) {
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current
    val badgeColor = when (entry.category) {
        LogCategory.TELEMETRY -> telemetryColors.channel1
        LogCategory.DOWNLINK -> colorScheme.primary
        LogCategory.SYSTEM -> telemetryColors.online
        LogCategory.MDNS -> telemetryColors.channel2
        LogCategory.IGNORED -> telemetryColors.offline
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.latencyMs?.let { lat ->
                    Text(
                        text = "+${lat}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = FormatUtils.formatClockMs(entry.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = entry.detail,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 13.dp)
        )
    }
}
