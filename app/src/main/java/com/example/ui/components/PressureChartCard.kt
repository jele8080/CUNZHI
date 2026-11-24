package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CunzhiProtocolSchema
import com.example.model.PressureSample
import com.example.ui.FormatUtils
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PressureChartCard(
    history: List<PressureSample>,
    p1Threshold: Double?,
    p2Threshold: Double?,
    autoScale: Boolean,
    onToggleAutoScale: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val colorScheme = MaterialTheme.colorScheme
    var inspectedIndex by remember(history.size) { mutableStateOf<Int?>(null) }

    val yMax = remember(history, autoScale, p1Threshold, p2Threshold) {
        if (!autoScale) {
            CunzhiProtocolSchema.MAX_PRESSURE_RAW
        } else {
            val maxSample = history.fold(0.0) { acc, sample ->
                max(acc, max(sample.first ?: 0.0, sample.second ?: 0.0))
            }
            val maxThresh = max(p1Threshold ?: 0.0, p2Threshold ?: 0.0)
            val rawPeak = max(maxSample, maxThresh)
            when {
                rawPeak <= 10.0 -> 20.0
                rawPeak <= 50.0 -> 60.0
                else -> (ceil((rawPeak * 1.15) / 50.0) * 50.0).coerceAtMost(CunzhiProtocolSchema.MAX_PRESSURE_RAW)
            }
        }
    }

    val p1Peak = remember(history) { history.mapNotNull { it.first }.maxOrNull() }
    val p2Peak = remember(history) { history.mapNotNull { it.second }.maxOrNull() }
    val inspectedSample = inspectedIndex?.let { idx -> history.getOrNull(idx) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pressure_chart_card"),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ShowChart,
                            contentDescription = "双路压力趋势图",
                            tint = telemetryColors.channel1,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "双路压力实时趋势",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "周期遥测采样 ${history.size}/${CunzhiProtocolSchema.MAX_HISTORY_POINTS} 点 · 量程 0–${yMax.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (autoScale) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleAutoScale)
                            .testTag("chart_scale_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoGraph,
                                contentDescription = "切换纵轴量程",
                                tint = if (autoScale) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (autoScale) "自适应" else "0–800",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoScale) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (history.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                inspectedIndex = null
                                onClearHistory()
                            },
                            modifier = Modifier.testTag("clear_chart_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "清空历史曲线",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend + Peak / Inspected Readout Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChannelLegendItem(
                        color = telemetryColors.channel1,
                        label = "P1 压力",
                        value = if (inspectedSample != null) {
                            FormatUtils.formatPressure(inspectedSample.first)
                        } else {
                            "峰值 ${FormatUtils.formatPressure(p1Peak)}"
                        }
                    )
                    ChannelLegendItem(
                        color = telemetryColors.channel2,
                        label = "P2 压力1",
                        value = if (inspectedSample != null) {
                            FormatUtils.formatPressure(inspectedSample.second)
                        } else {
                            "峰值 ${FormatUtils.formatPressure(p2Peak)}"
                        }
                    )
                }

                if (inspectedSample != null) {
                    Text(
                        text = FormatUtils.formatClockShort(inspectedSample.at),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "等待周期遥测压力帧 (method: update)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "仅采集周期压力帧，避免与 10s 完整上报重复",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    // Y-Axis Scale Labels
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .height(190.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${yMax.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${(yMax / 2).toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    val gridColor = telemetryColors.gridLine.copy(alpha = 0.38f)
                    val ch1Color = telemetryColors.channel1
                    val ch2Color = telemetryColors.channel2
                    val crosshairColor = colorScheme.onSurface.copy(alpha = 0.55f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 32.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                            .pointerInput(history) {
                                detectTapGestures { offset ->
                                    if (history.size == 1) {
                                        inspectedIndex = if (inspectedIndex == 0) null else 0
                                    } else if (history.size > 1 && size.width > 0) {
                                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val idx = (ratio * (history.size - 1)).roundToInt()
                                        inspectedIndex = if (inspectedIndex == idx) null else idx
                                    }
                                }
                            }
                            .pointerInput(history) {
                                detectHorizontalDragGestures(
                                    onDragEnd = { },
                                    onHorizontalDrag = { change, _ ->
                                        if (history.size > 1 && size.width > 0) {
                                            val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            inspectedIndex = (ratio * (history.size - 1)).roundToInt()
                                        }
                                    }
                                )
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f) return@Canvas

                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        // Horizontal reference grid (0%, 25%, 50%, 75%, 100%)
                        for (step in 0..4) {
                            val y = h * (step / 4f)
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = if (step == 0 || step == 4) null else dashEffect
                            )
                        }

                        fun yFor(value: Double): Float {
                            val norm = (value / yMax).coerceIn(0.0, 1.0)
                            return (h * (1.0 - norm)).toFloat()
                        }

                        fun xFor(index: Int): Float {
                            return if (history.size <= 1) w else w * (index.toFloat() / (history.size - 1).toFloat())
                        }

                        // Optional threshold guide lines
                        p1Threshold?.takeIf { it > 0.0 && it <= yMax }?.let { thresh ->
                            val ty = yFor(thresh)
                            drawLine(
                                color = ch1Color.copy(alpha = 0.45f),
                                start = Offset(0f, ty),
                                end = Offset(w, ty),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            )
                        }
                        p2Threshold?.takeIf { it > 0.0 && it <= yMax }?.let { thresh ->
                            val ty = yFor(thresh)
                            drawLine(
                                color = ch2Color.copy(alpha = 0.45f),
                                start = Offset(0f, ty),
                                end = Offset(w, ty),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            )
                        }

                        // Helper that breaks segments cleanly on null values (never plotting missing data as 0)
                        fun drawChannel(selector: (PressureSample) -> Double?, strokeColor: Color) {
                            val segments = mutableListOf<List<Offset>>()
                            var currentSegment = mutableListOf<Offset>()
                            history.forEachIndexed { idx, sample ->
                                val v = selector(sample)
                                if (v != null) {
                                    currentSegment.add(Offset(xFor(idx), yFor(v)))
                                } else if (currentSegment.isNotEmpty()) {
                                    segments.add(currentSegment)
                                    currentSegment = mutableListOf()
                                }
                            }
                            if (currentSegment.isNotEmpty()) {
                                segments.add(currentSegment)
                            }

                            for (seg in segments) {
                                if (seg.size == 1) {
                                    drawCircle(
                                        color = strokeColor,
                                        radius = 3.5.dp.toPx(),
                                        center = seg.first()
                                    )
                                } else {
                                    val linePath = Path().apply {
                                        moveTo(seg.first().x, seg.first().y)
                                        for (i in 1 until seg.size) {
                                            lineTo(seg[i].x, seg[i].y)
                                        }
                                    }
                                    val fillPath = Path().apply {
                                        moveTo(seg.first().x, h)
                                        seg.forEach { pt -> lineTo(pt.x, pt.y) }
                                        lineTo(seg.last().x, h)
                                        close()
                                    }
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                strokeColor.copy(alpha = 0.22f),
                                                Color.Transparent
                                            ),
                                            startY = 0f,
                                            endY = h
                                        )
                                    )
                                    drawPath(
                                        path = linePath,
                                        color = strokeColor,
                                        style = Stroke(
                                            width = 2.4.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }

                        drawChannel({ it.second }, ch2Color)
                        drawChannel({ it.first }, ch1Color)

                        // Draw interactive scrub crosshair if user tapped/dragged
                        inspectedIndex?.let { idx ->
                            val sample = history.getOrNull(idx) ?: return@let
                            val x = xFor(idx)
                            drawLine(
                                color = crosshairColor,
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )
                            sample.first?.let { v1 ->
                                drawCircle(
                                    color = ch1Color,
                                    radius = 5.dp.toPx(),
                                    center = Offset(x, yFor(v1))
                                )
                            }
                            sample.second?.let { v2 ->
                                drawCircle(
                                    color = ch2Color,
                                    radius = 5.dp.toPx(),
                                    center = Offset(x, yFor(v2))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = FormatUtils.formatClockShort(history.first().at),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (inspectedIndex != null) "再次点击取消标尺" else "左右滑动查看历史单点",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = FormatUtils.formatClockShort(history.last().at),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelLegendItem(
    color: Color,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
