package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CunzhiProtocolSchema
import com.example.model.DeviceFieldSpec
import com.example.model.FieldGroup
import com.example.model.FieldType
import com.example.ui.FormatUtils
import com.example.ui.MonitorUiState
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun PropertiesScreen(
    uiState: MonitorUiState,
    onSelectDevice: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onReadProperty: (mac: String, key: String) -> Unit,
    onWriteProperty: (mac: String, key: String, value: Any) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current
    val device = uiState.selectedDevice
    val query = uiState.propertySearchQuery.trim().lowercase()
    var editingSpec by remember { mutableStateOf<DeviceFieldSpec?>(null) }

    val groupedFields = remember(query) {
        FieldGroup.entries.mapNotNull { group ->
            val items = CunzhiProtocolSchema.fields.filter { spec ->
                spec.group == group && (
                    query.isEmpty() ||
                        spec.key.lowercase().contains(query) ||
                        spec.label.lowercase().contains(query) ||
                        spec.description.lowercase().contains(query)
                    )
            }
            if (items.isEmpty()) null else group to items
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "设备属性读写表",
                                style = MaterialTheme.typography.headlineSmall,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (device != null) {
                                    "MAC ${device.mac} · 点击「可写」行编辑下发，点击刷新图标发送 get"
                                } else {
                                    "共 ${CunzhiProtocolSchema.fields.size} 项协议属性（20 项可写 · 5 项只读）"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (uiState.devices.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        DeviceSelectorStrip(
                            devices = uiState.devices,
                            selectedMac = device?.mac,
                            onSelectDevice = onSelectDevice
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = uiState.propertySearchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("property_search_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text(
                                text = "搜索字段名或中文说明（如 power / report_delay_ms）",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索属性"
                            )
                        },
                        trailingIcon = {
                            if (uiState.propertySearchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "清除搜索"
                                    )
                                }
                            }
                        }
                    )
                }
            }

            if (device == null) {
                item(key = "no_device_notice") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "尚未收到设备上报，以下展示全量字段定义；设备上线后可直接读取 (get) 或写入 (set) 全部 20 项可写属性。",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            groupedFields.forEach { (group, specs) ->
                item(key = "group_${group.name}") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = colorScheme.surface,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TableRows,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.primary
                                )
                            }

                            specs.forEachIndexed { index, spec ->
                                val rawVal = device?.fields?.get(spec.key)
                                PropertyRowItem(
                                    spec = spec,
                                    rawValue = rawVal,
                                    canInteract = device != null,
                                    highlightColor = when (spec.key) {
                                        "pressure" -> telemetryColors.channel1
                                        "pressure1" -> telemetryColors.channel2
                                        else -> null
                                    },
                                    onReadClick = {
                                        device?.let { onReadProperty(it.mac, spec.key) }
                                    },
                                    onEditClick = {
                                        if (device != null && spec.writable) {
                                            editingSpec = spec
                                        }
                                    }
                                )
                                if (index < specs.lastIndex) {
                                    HorizontalDivider(
                                        color = colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (device != null && device.unknownFields.isNotEmpty()) {
                item(key = "unknown_fields") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = colorScheme.surface,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "其他未声明字段（仅记录供调试）",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            device.unknownFields.entries.forEach { (k, v) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = k,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = v,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurface
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

    val specToEdit = editingSpec
    if (specToEdit != null && device != null) {
        EditPropertyDialog(
            spec = specToEdit,
            currentValue = device.fields[specToEdit.key],
            maxVoltageLimitV = device.maxVoltageLimitV,
            onDismiss = { editingSpec = null },
            onRead = {
                onReadProperty(device.mac, specToEdit.key)
            },
            onConfirmWrite = { typedVal ->
                onWriteProperty(device.mac, specToEdit.key, typedVal)
                editingSpec = null
            }
        )
    }
}

@Composable
private fun PropertyRowItem(
    spec: DeviceFieldSpec,
    rawValue: Any?,
    canInteract: Boolean,
    highlightColor: Color?,
    onReadClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val displayValue = when {
        spec.key == "safe" && rawValue is Number -> when (rawValue.toInt()) {
            1 -> "1 (36V)"
            0 -> "0 (75V)"
            else -> rawValue.toString()
        }
        spec.key == "shock" && rawValue is Number -> when (rawValue.toInt()) {
            1 -> "1 (开)"
            0 -> "0 (关)"
            else -> rawValue.toString()
        }
        else -> FormatUtils.formatFieldValue(rawValue, spec.unit)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (canInteract && spec.writable) {
                    Modifier.clickable(onClick = onEditClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp)
            .testTag("property_row_${spec.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (spec.writable) colorScheme.primaryContainer else colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (spec.writable) "可写" else "只读",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (spec.writable) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${spec.key} · ${spec.description}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = when {
                        rawValue == null -> colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        highlightColor != null -> highlightColor
                        else -> colorScheme.onSurface
                    }
                )
                if (spec.defaultHint.isNotEmpty()) {
                    Text(
                        text = "默认 ${spec.defaultHint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            if (canInteract) {
                Spacer(modifier = Modifier.width(4.dp))
                if (spec.writable) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_property_${spec.key}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "编辑写入 ${spec.label}",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onReadClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("read_property_${spec.key}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "读取 ${spec.label}",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPropertyDialog(
    spec: DeviceFieldSpec,
    currentValue: Any?,
    maxVoltageLimitV: Int,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onConfirmWrite: (Any) -> Unit
) {
    var inputText by remember(spec.key, currentValue) {
        mutableStateOf(
            when (currentValue) {
                is Double -> if (currentValue % 1.0 == 0.0) currentValue.toLong().toString() else currentValue.toString()
                null -> ""
                else -> currentValue.toString()
            }
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val quickPresets: List<Pair<String, String>> = remember(spec.key, maxVoltageLimitV) {
        when (spec.key) {
            "report_delay_ms" -> listOf("100" to "100", "500" to "500", "1000" to "1000", "5000" to "5000")
            "sleep_time" -> listOf("600" to "600s", "3600" to "3600s", "7200" to "7200s")
            "safe" -> listOf("1" to "1 (上限 36V)", "0" to "0 (上限 75V)")
            "power", "game_m_power" -> listOf("0", "25", "50", "80", "100").map { it to it }
            "voltage", "game_e_vol" -> if (maxVoltageLimitV <= CunzhiProtocolSchema.SAFE_VOLTAGE_LIMIT_V) {
                listOf("0", "10", "18", "24", "36").map { it to "${it}V" }
            } else {
                listOf("0", "15", "36", "50", "75").map { it to "${it}V" }
            }
            "shock" -> listOf("0" to "0 (关闭)", "1" to "1 (开启)")
            "delay" -> listOf("20", "100", "200", "500", "1000").map { it to "${it}ms" }
            "game_e_dur", "game_m_dur", "game_cooldown" -> listOf("0", "200", "500", "1000").map { it to it }
            "game_p1_thresh", "game_p2_thresh" -> listOf("0", "100", "200", "400").map { it to it }
            "game_mode" -> listOf("0", "1", "2", "3").map { it to it }
            "game_cz_count" -> listOf("0" to "0")
            else -> emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "写入 ${spec.label}")
                Text(
                    text = "${spec.key} (${spec.type.name}${if (spec.unit.isNotEmpty()) ", ${spec.unit}" else ""})",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val hintSuffix = if (spec.key == "voltage" || spec.key == "game_e_vol") {
                    "（当前安全上限：0–${maxVoltageLimitV}V）"
                } else if (spec.defaultHint.isNotEmpty()) {
                    "（默认 ${spec.defaultHint}）"
                } else {
                    ""
                }
                Text(
                    text = spec.description + hintSuffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        validationError = null
                    },
                    label = { Text("目标值") },
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = validationError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (spec.type) {
                            FieldType.INT -> KeyboardType.Number
                            FieldType.FLOAT -> KeyboardType.Decimal
                            FieldType.STRING -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_property_dialog_input")
                )

                if (quickPresets.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickPresets.forEach { (presetVal, label) ->
                            FilterChip(
                                selected = inputText == presetVal,
                                onClick = {
                                    inputText = presetVal
                                    validationError = null
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = onRead,
                    modifier = Modifier.testTag("edit_property_dialog_read_btn")
                ) {
                    Text("读取 get")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = inputText.trim()
                    val parsed: Any? = when (spec.type) {
                        FieldType.INT -> trimmed.toIntOrNull()
                        FieldType.FLOAT -> trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }
                        FieldType.STRING -> trimmed.ifEmpty { null }
                    }
                    if (parsed == null) {
                        validationError = when (spec.type) {
                            FieldType.INT -> "请输入有效整数"
                            FieldType.FLOAT -> "请输入有效数值"
                            FieldType.STRING -> "内容不能为空"
                        }
                    } else if (spec.key == "report_delay_ms" && (parsed as Int) !in 100..10000) {
                        validationError = "协议允许范围为 100–10000 ms"
                    } else if (spec.key == "safe" && (parsed as Int) !in 0..1) {
                        validationError = "safe 仅支持 1（上限 36V）或 0（上限 75V）"
                    } else if (spec.key == "shock" && (parsed as Int) !in 0..1) {
                        validationError = "shock 仅支持 0（关闭）或 1（开启）"
                    } else if (spec.key == "delay" && (parsed as Int) !in CunzhiProtocolSchema.MIN_STIM_DELAY_MS..CunzhiProtocolSchema.MAX_STIM_DELAY_MS) {
                        validationError = "delay 允许范围为 ${CunzhiProtocolSchema.MIN_STIM_DELAY_MS}–${CunzhiProtocolSchema.MAX_STIM_DELAY_MS} ms"
                    } else if ((spec.key == "voltage" || spec.key == "game_e_vol") && (parsed as Int) !in 0..maxVoltageLimitV) {
                        validationError = if (maxVoltageLimitV <= CunzhiProtocolSchema.SAFE_VOLTAGE_LIMIT_V) {
                            "当前 safe=1 限压 0–36V，如需更高电压请先将 safe 设为 0"
                        } else {
                            "当前电压范围为 0–${maxVoltageLimitV}V"
                        }
                    } else {
                        onConfirmWrite(parsed)
                    }
                },
                modifier = Modifier.testTag("edit_property_dialog_write_btn")
            ) {
                Text("写入 set")
            }
        }
    )
}
