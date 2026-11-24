package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CunzhiProtocolSchema
import com.example.model.DeviceSnapshot
import com.example.model.DownlinkFormat
import com.example.model.DownlinkResult
import com.example.ui.MonitorUiState
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(
    uiState: MonitorUiState,
    onSelectDevice: (String) -> Unit,
    onSetDownlinkFormat: (DownlinkFormat) -> Unit,
    onWriteProperty: (mac: String, key: String, value: Any) -> Unit,
    onQueryCoreFields: (mac: String) -> Unit,
    onRunMotorPulse: (mac: String, power: Int, durationMs: Long) -> Unit,
    onStopMotor: (mac: String) -> Unit,
    onRunStimPulse: (mac: String, voltage: Int, delayVal: Int, shockLevel: Int, autoResetMs: Long) -> Unit,
    onStopStim: (mac: String) -> Unit,
    onEmergencyStop: (mac: String) -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current
    val device = uiState.selectedDevice

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
            item(key = "control_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "电机与电刺激控制测试",
                                style = MaterialTheme.typography.headlineSmall,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (device != null) {
                                    "下行主题 /drecv/${device.mac} · QoS 1"
                                } else {
                                    "等待设备接入后可下发控制与测试指令"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        if (device != null) {
                            FilledTonalButton(
                                onClick = { onQueryCoreFields(device.mac) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("query_core_fields_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "读取状态",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("读取状态")
                            }
                        }
                    }

                    if (uiState.devices.size > 1) {
                        DeviceSelectorStrip(
                            devices = uiState.devices,
                            selectedMac = device?.mac,
                            onSelectDevice = onSelectDevice
                        )
                    }

                    // Prominent Emergency Stop Bar (Always reachable!)
                    if (device != null) {
                        Button(
                            onClick = { onEmergencyStop(device.mac) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = telemetryColors.offline,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("emergency_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dangerous,
                                contentDescription = "急停",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "一键急停 · 电机与电刺激全部归零",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Downlink result banner
            uiState.controlTest.lastDownlinkResult?.let { result ->
                item(key = "last_downlink_result") {
                    LastDownlinkResultBanner(result = result)
                }
            }

            if (device == null) {
                item(key = "no_device_control") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.surface,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "尚未发现可控制的 CUNZHI01 设备",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "设备通过 MQTT 上报一次后，即可自动识别 MAC 并通过 /drecv/<mac> 下发电机、电刺激测试指令及可写参数。",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(onClick = onOpenConnection) {
                                Text("查看连接与日志")
                            }
                        }
                    }
                }
            } else {
                // Downlink JSON format selector
                item(key = "downlink_format_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "下发 JSON 格式（按固件兼容切换）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (uiState.config.downlinkFormat == DownlinkFormat.KEY_VALUE) {
                                        """{"method":"set","key":...,"value":...}"""
                                    } else {
                                        """{"method":"set","power":50}"""
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = colorScheme.primary
                                )
                            }
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("downlink_format_selector")
                            ) {
                                DownlinkFormat.entries.forEachIndexed { idx, fmt ->
                                    SegmentedButton(
                                        selected = uiState.config.downlinkFormat == fmt,
                                        onClick = { onSetDownlinkFormat(fmt) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = idx,
                                            count = DownlinkFormat.entries.size
                                        )
                                    ) {
                                        Text(fmt.label)
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Motor Test Panel
                item(key = "motor_test_card") {
                    MotorTestCard(
                        device = device,
                        remainingMs = uiState.controlTest.motorPulseRemainingMs,
                        totalMs = uiState.controlTest.motorPulseTotalMs,
                        onSetPower = { power -> onWriteProperty(device.mac, "power", power) },
                        onRunPulse = { power, durMs -> onRunMotorPulse(device.mac, power, durMs) },
                        onStopMotor = { onStopMotor(device.mac) }
                    )
                }

                // 2. Electro-Stimulation Test Panel (with Safety Interlock)
                item(key = "stim_test_card") {
                    ElectroStimTestCard(
                        device = device,
                        pulseActive = uiState.controlTest.stimPulseActive,
                        onWriteField = { key, value -> onWriteProperty(device.mac, key, value) },
                        onRunStimPulse = { voltage, delayVal, shock, autoResetMs ->
                            onRunStimPulse(device.mac, voltage, delayVal, shock, autoResetMs)
                        },
                        onStopStim = { onStopStim(device.mac) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun LastDownlinkResultBanner(result: DownlinkResult) {
    val telemetryColors = LocalTelemetryColors.current
    val tint = if (result.success) telemetryColors.online else telemetryColors.offline
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downlink_result_banner"),
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${result.topic} · ${result.payload}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotorTestCard(
    device: DeviceSnapshot,
    remainingMs: Long,
    totalMs: Long,
    onSetPower: (Int) -> Unit,
    onRunPulse: (Int, Long) -> Unit,
    onStopMotor: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current

    val currentPower = (device.fields["power"] as? Number)?.toInt()
    val gameMPower = (device.fields["game_m_power"] as? Number)?.toInt()
    val gameMDur = (device.fields["game_m_dur"] as? Number)?.toInt()

    var powerText by remember { mutableStateOf((currentPower ?: 30).toString()) }
    var pulseDurationMs by remember { mutableLongStateOf(1_000L) }
    val parsedPower = powerText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val isPulsing = remainingMs > 0L && totalMs > 0L

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("motor_test_card"),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, telemetryColors.channel1.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Vibration,
                        contentDescription = null,
                        tint = telemetryColors.channel1,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "电机测试 (power)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "直接写入 power 字段，或执行定时脉冲测试后自动归零",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Current readback strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadbackStatBox(
                    label = "当前 power",
                    value = currentPower?.toString() ?: "—",
                    accent = telemetryColors.channel1,
                    modifier = Modifier.weight(1f)
                )
                ReadbackStatBox(
                    label = "模式强度",
                    value = gameMPower?.toString() ?: "—",
                    modifier = Modifier.weight(1f)
                )
                ReadbackStatBox(
                    label = "模式时长",
                    value = gameMDur?.let { "${it}ms" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }

            // Slider + Direct numeric input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = parsedPower.coerceIn(0, 100).toFloat(),
                    onValueChange = { powerText = it.roundToInt().toString() },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = telemetryColors.channel1,
                        activeTrackColor = telemetryColors.channel1
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("motor_power_slider")
                )
                OutlinedTextField(
                    value = powerText,
                    onValueChange = { powerText = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("数值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .width(84.dp)
                        .testTag("motor_power_input")
                )
            }

            // Quick presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0, 20, 40, 60, 80, 100).forEach { preset ->
                    FilterChip(
                        selected = parsedPower == preset,
                        onClick = { powerText = preset.toString() },
                        label = { Text(if (preset == 0) "0 停" else "$preset") }
                    )
                }
            }

            // Direct Continuous Output Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onSetPower(parsedPower) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("motor_send_power_button")
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("下发 power=$parsedPower")
                }
                OutlinedButton(
                    onClick = {
                        powerText = "0"
                        onStopMotor()
                    },
                    modifier = Modifier.testTag("motor_stop_button")
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("停止")
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))

            // Timed Pulse Test Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "定时脉冲测试（到时自动发送 power=0）",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(500L to "0.5s", 1_000L to "1s", 2_000L to "2s", 3_000L to "3s").forEach { (ms, label) ->
                        FilterChip(
                            selected = pulseDurationMs == ms,
                            onClick = { pulseDurationMs = ms },
                            label = { Text(label) }
                        )
                    }
                }

                if (isPulsing) {
                    val progress = (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = telemetryColors.channel1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Text(
                            text = "脉冲输出中 · 剩余 ${String.format("%.1f", remainingMs / 1000.0)}s 后自动归零",
                            style = MaterialTheme.typography.labelSmall,
                            color = telemetryColors.channel1
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        val level = if (parsedPower > 0) parsedPower else 30
                        powerText = level.toString()
                        onRunPulse(level, pulseDurationMs)
                    },
                    enabled = !isPulsing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("motor_pulse_test_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("启动定时脉冲测试 (power=${if (parsedPower > 0) parsedPower else 30}, ${pulseDurationMs / 1000.0}s)")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElectroStimTestCard(
    device: DeviceSnapshot,
    pulseActive: Boolean,
    onWriteField: (key: String, value: Any) -> Unit,
    onRunStimPulse: (voltage: Int, delayVal: Int, shockLevel: Int, autoResetMs: Long) -> Unit,
    onStopStim: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current

    val currentSafe = device.safeMode
    val currentVoltage = (device.fields["voltage"] as? Number)?.toInt()
    val currentShock = (device.fields["shock"] as? Number)?.toInt()
    val currentDelay = (device.fields["delay"] as? Number)?.toInt()

    // Locked by default every time user enters to prevent accidental electro-stimulation triggers
    var unlocked by remember { mutableStateOf(false) }

    // Effective safe state: default fail-safe to 1 (36V) when unknown
    var targetSafe by remember { mutableStateOf(if (currentSafe == 0) 0 else 1) }
    LaunchedEffect(currentSafe) {
        if (currentSafe == 0 || currentSafe == 1) {
            targetSafe = currentSafe
        }
    }

    val maxVoltageV = if (targetSafe == 0) {
        CunzhiProtocolSchema.UNSAFE_VOLTAGE_LIMIT_V
    } else {
        CunzhiProtocolSchema.SAFE_VOLTAGE_LIMIT_V
    }

    var voltageText by remember { mutableStateOf((currentVoltage ?: 10).coerceIn(0, maxVoltageV).toString()) }
    var delayText by remember {
        mutableStateOf(
            (currentDelay ?: 200)
                .coerceIn(CunzhiProtocolSchema.MIN_STIM_DELAY_MS, CunzhiProtocolSchema.MAX_STIM_DELAY_MS)
                .toString()
        )
    }
    var autoResetMs by remember { mutableLongStateOf(500L) }

    // Clamp draft voltage immediately if switching back to safe=1 (36V limit)
    LaunchedEffect(maxVoltageV) {
        val currentDraft = voltageText.toIntOrNull() ?: 0
        if (currentDraft > maxVoltageV) {
            voltageText = maxVoltageV.toString()
        }
    }

    val parsedVoltage = (voltageText.toIntOrNull() ?: 0).coerceIn(0, maxVoltageV)
    val parsedDelay = (delayText.toIntOrNull() ?: CunzhiProtocolSchema.MIN_STIM_DELAY_MS)
        .coerceIn(CunzhiProtocolSchema.MIN_STIM_DELAY_MS, CunzhiProtocolSchema.MAX_STIM_DELAY_MS)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stim_test_card"),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (unlocked) telemetryColors.channel2.copy(alpha = 0.7f) else colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Safety Interlock Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = telemetryColors.channel2,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "电刺激测试 (voltage / shock / delay / safe)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = if (unlocked) {
                                "已解锁 · 当前电压上限 ${maxVoltageV}V（safe=$targetSafe）"
                            } else {
                                "默认锁定防误触，开启右侧安全锁后可测试"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unlocked) telemetryColors.channel2 else colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (unlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        contentDescription = "安全解锁",
                        tint = if (unlocked) telemetryColors.channel2 else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = unlocked,
                        onCheckedChange = { unlocked = it },
                        modifier = Modifier.testTag("stim_safety_unlock_switch")
                    )
                }
            }

            // Live readback row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadbackStatBox(
                    label = "上限 (safe)",
                    value = when (currentSafe) {
                        1 -> "36V (1)"
                        0 -> "75V (0)"
                        else -> "—"
                    },
                    accent = if (currentSafe == 0) telemetryColors.warning else telemetryColors.online,
                    modifier = Modifier.weight(1.15f)
                )
                ReadbackStatBox(
                    label = "voltage",
                    value = currentVoltage?.let { "${it}V" } ?: "—",
                    accent = telemetryColors.channel2,
                    modifier = Modifier.weight(0.95f)
                )
                ReadbackStatBox(
                    label = "shock",
                    value = when (currentShock) {
                        1 -> "开 (1)"
                        0 -> "关 (0)"
                        else -> "—"
                    },
                    accent = if (currentShock == 1) telemetryColors.channel2 else colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.9f)
                )
                ReadbackStatBox(
                    label = "delay",
                    value = currentDelay?.let { "${it}ms" } ?: "—",
                    modifier = Modifier.weight(0.95f)
                )
            }

            // Always-available Stop Stimulation Button
            OutlinedButton(
                onClick = {
                    voltageText = "0"
                    onStopStim()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stim_stop_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    tint = telemetryColors.offline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("立即停止电刺激 (shock=0, voltage=0V)", color = telemetryColors.offline)
            }

            AnimatedVisibility(visible = unlocked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))

                    // Safe voltage ceiling segmented selector (safe=1 -> 36V, safe=0 -> 75V)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "电压上限档位 (safe)",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (targetSafe == 1) "安全限压 · 最高 36V" else "高压档 · 最高 75V",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (targetSafe == 1) telemetryColors.online else telemetryColors.warning
                            )
                        }

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stim_safe_segmented")
                        ) {
                            SegmentedButton(
                                selected = targetSafe == 1,
                                onClick = {
                                    targetSafe = 1
                                    if ((voltageText.toIntOrNull() ?: 0) > CunzhiProtocolSchema.SAFE_VOLTAGE_LIMIT_V) {
                                        voltageText = CunzhiProtocolSchema.SAFE_VOLTAGE_LIMIT_V.toString()
                                    }
                                    onWriteField("safe", 1)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                modifier = Modifier.testTag("stim_safe_1_btn")
                            ) {
                                Text("safe=1 · 上限 36V")
                            }
                            SegmentedButton(
                                selected = targetSafe == 0,
                                onClick = {
                                    targetSafe = 0
                                    onWriteField("safe", 0)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                modifier = Modifier.testTag("stim_safe_0_btn")
                            ) {
                                Text("safe=0 · 上限 75V")
                            }
                        }
                    }

                    // Voltage slider + direct input dynamically clamped to 36V or 75V
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "电刺激电压 (voltage)",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "0–${maxVoltageV}V",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = telemetryColors.channel2
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = parsedVoltage.toFloat(),
                                onValueChange = { voltageText = it.roundToInt().toString() },
                                valueRange = 0f..maxVoltageV.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = telemetryColors.channel2,
                                    activeTrackColor = telemetryColors.channel2
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stim_voltage_slider")
                            )
                            OutlinedTextField(
                                value = voltageText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { ch -> ch.isDigit() }.take(3)
                                    val clamped = digits.toIntOrNull()?.coerceIn(0, maxVoltageV)
                                    voltageText = clamped?.toString() ?: ""
                                },
                                suffix = { Text("V") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .width(86.dp)
                                    .testTag("stim_voltage_input")
                            )
                            FilledTonalButton(
                                onClick = { onWriteField("voltage", parsedVoltage) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("stim_voltage_send_button")
                            ) {
                                Text("写入")
                            }
                        }

                        val voltagePresets = if (targetSafe == 1) {
                            listOf(0, 10, 18, 24, 36)
                        } else {
                            listOf(0, 15, 36, 50, 65, 75)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            voltagePresets.forEach { preset ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (parsedVoltage == preset) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { voltageText = preset.toString() }
                                ) {
                                    Text(
                                        text = "${preset}V",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = if (parsedVoltage == preset) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Delay slider + direct input strictly bounded to 20–1000 ms
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "电刺激延时 (delay)",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "${CunzhiProtocolSchema.MIN_STIM_DELAY_MS}–${CunzhiProtocolSchema.MAX_STIM_DELAY_MS} ms",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = parsedDelay.toFloat(),
                                onValueChange = { delayText = it.roundToInt().toString() },
                                valueRange = CunzhiProtocolSchema.MIN_STIM_DELAY_MS.toFloat()..CunzhiProtocolSchema.MAX_STIM_DELAY_MS.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stim_delay_slider")
                            )
                            OutlinedTextField(
                                value = delayText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { ch -> ch.isDigit() }.take(4)
                                    val num = digits.toIntOrNull()
                                    delayText = when {
                                        digits.isEmpty() -> ""
                                        num != null && num > CunzhiProtocolSchema.MAX_STIM_DELAY_MS ->
                                            CunzhiProtocolSchema.MAX_STIM_DELAY_MS.toString()
                                        else -> digits
                                    }
                                },
                                suffix = { Text("ms") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .width(96.dp)
                                    .testTag("stim_delay_input")
                            )
                            FilledTonalButton(
                                onClick = {
                                    delayText = parsedDelay.toString()
                                    onWriteField("delay", parsedDelay)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("stim_delay_send_button")
                            ) {
                                Text("写入")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(20, 100, 200, 500, 1000).forEach { preset ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (parsedDelay == preset) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { delayText = preset.toString() }
                                ) {
                                    Text(
                                        text = "${preset}ms",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = if (parsedDelay == preset) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Shock strict 0/1 binary switch
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "电刺激开关 (shock)",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "仅支持 0（关）或 1（开）",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stim_shock_segmented")
                        ) {
                            SegmentedButton(
                                selected = currentShock == 0,
                                onClick = { onWriteField("shock", 0) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                modifier = Modifier.testTag("stim_shock_0_btn")
                            ) {
                                Text("shock=0 · 关闭")
                            }
                            SegmentedButton(
                                selected = currentShock == 1,
                                onClick = { onWriteField("shock", 1) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                modifier = Modifier.testTag("stim_shock_1_btn")
                            ) {
                                Text("shock=1 · 开启")
                            }
                        }
                    }

                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))

                    // Combined Single Pulse Test (always fires shock=1, then optionally resets shock=0)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "组合点动测试（依次下发 voltage → delay → shock=1，超时自动归零 shock=0）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                300L to "300ms 归零",
                                500L to "500ms 归零",
                                1_000L to "1s 归零",
                                0L to "仅开启不归零"
                            ).forEach { (ms, label) ->
                                FilterChip(
                                    selected = autoResetMs == ms,
                                    onClick = { autoResetMs = ms },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onRunStimPulse(parsedVoltage, parsedDelay, 1, autoResetMs)
                            },
                            enabled = !pulseActive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = telemetryColors.channel2,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stim_pulse_test_button")
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (pulseActive) {
                                    "电刺激脉冲执行中..."
                                } else {
                                    "触发电刺激测试 (${parsedVoltage}V, ${parsedDelay}ms, shock=1)"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadbackStatBox(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color? = null,
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
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = accent ?: colorScheme.onSurface
        )
    }
}
