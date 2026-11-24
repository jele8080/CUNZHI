package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.RuntimeStatus
import com.example.service.BackgroundHealthStatus
import com.example.service.XiaomiBackgroundHelper
import com.example.ui.theme.LocalTelemetryColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun XiaomiKeepAliveCard(
    health: BackgroundHealthStatus,
    runtime: RuntimeStatus,
    keepAliveLocksEnabled: Boolean,
    autoRecoverNetwork: Boolean,
    onToggleKeepAliveLocks: (Boolean) -> Unit,
    onToggleAutoRecoverNetwork: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("xiaomi_keep_alive_card"),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (health.allCoreChecksPassed) {
                telemetryColors.online.copy(alpha = 0.4f)
            } else {
                telemetryColors.warning.copy(alpha = 0.55f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = if (health.allCoreChecksPassed) telemetryColors.online else telemetryColors.warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "小米 / HyperOS 后台保活优化",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        val subtitle = if (health.deviceDisplayName.isNotBlank()) {
                            "当前机型：${health.deviceDisplayName}"
                        } else {
                            "防止息屏后断连或 mDNS 广播休眠"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (health.allCoreChecksPassed) {
                        telemetryColors.online.copy(alpha = 0.16f)
                    } else {
                        telemetryColors.warning.copy(alpha = 0.16f)
                    }
                ) {
                    Text(
                        text = if (health.allCoreChecksPassed) "基础项已就绪" else "待优化",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (health.allCoreChecksPassed) telemetryColors.online else telemetryColors.warning,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Live Hardware Keep-Alive Lock Badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RuntimeLockBadge(
                    label = "CPU 唤醒锁",
                    active = runtime.wakeLockActive
                )
                RuntimeLockBadge(
                    label = "Wi-Fi 低延迟锁",
                    active = runtime.wifiLockActive
                )
                RuntimeLockBadge(
                    label = if (runtime.networkRecoveries > 0) {
                        "网络自愈 (${runtime.networkRecoveries})"
                    } else {
                        "网络自愈监听"
                    },
                    active = autoRecoverNetwork
                )
            }

            // In-App Switches for Xiaomi Doze / Wi-Fi Sleep Mitigation
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "息屏防休眠锁（CPU + Wi-Fi 低延迟）",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "防止小米息屏后冻结 TCP 1883 或丢弃 UDP 5353 mDNS 包",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = keepAliveLocksEnabled,
                            onCheckedChange = onToggleKeepAliveLocks,
                            modifier = Modifier.testTag("xiaomi_keep_alive_locks_switch")
                        )
                    }

                    HorizontalDivider(
                        color = colorScheme.outlineVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi-Fi 切网 / 休眠唤醒自动自愈",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "检测到 Wi-Fi IP 变动或重连时自动重新宣告 easysmart.local",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoRecoverNetwork,
                            onCheckedChange = onToggleAutoRecoverNetwork,
                            modifier = Modifier.testTag("xiaomi_network_recover_switch")
                        )
                    }
                }
            }

            // One-Tap Xiaomi System Settings Actions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "小米系统必做设置（点击直达系统页面）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )

                XiaomiActionRow(
                    icon = Icons.Rounded.BatterySaver,
                    title = "1. 省电策略设为「无限制」",
                    subtitle = if (health.ignoringBatteryOptimizations) {
                        "当前已加入电池优化白名单，息屏不会被智能限制"
                    } else {
                        "当前受系统省电限制，强烈建议改为「无限制」"
                    },
                    statusText = if (health.ignoringBatteryOptimizations) "已放行" else "去设置",
                    statusPositive = health.ignoringBatteryOptimizations,
                    onClick = { XiaomiBackgroundHelper.openBatteryStrategySettings(context) },
                    testTag = "xiaomi_battery_strategy_row"
                )

                XiaomiActionRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = "2. 开启「允许自启动」",
                    subtitle = "防止系统内存回收或重连后无法自动恢复后台监听",
                    statusText = "去检查",
                    statusPositive = null,
                    onClick = { XiaomiBackgroundHelper.openAutoStartSettings(context) },
                    testTag = "xiaomi_autostart_row"
                )

                XiaomiActionRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "3. 允许常驻状态栏通知",
                    subtitle = if (health.notificationsEnabled) {
                        "通知权限正常，前台服务优先级受保护"
                    } else {
                        "通知被关闭会导致小米大幅降低后台服务存活优先级"
                    },
                    statusText = if (health.notificationsEnabled) "已开启" else "未开启",
                    statusPositive = health.notificationsEnabled,
                    onClick = { XiaomiBackgroundHelper.openNotificationSettings(context) },
                    testTag = "xiaomi_notification_row"
                )

                XiaomiActionRow(
                    icon = Icons.Rounded.Security,
                    title = "4. 应用权限与后台联网",
                    subtitle = "确认未限制「WLAN 后台联网」与本地局域网通信",
                    statusText = "去查看",
                    statusPositive = null,
                    onClick = { XiaomiBackgroundHelper.openXiaomiPermissions(context) },
                    testTag = "xiaomi_permissions_row"
                )
            }

            // Step 5: Manual Recents Card Lock Tip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "第 5 步（手动）：上滑进入最近任务界面，长按「寸止监看」卡片并点击小锁图标，可避免点击一键清理时误杀服务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeLockBadge(
    label: String,
    active: Boolean
) {
    val telemetryColors = LocalTelemetryColors.current
    val colorScheme = MaterialTheme.colorScheme
    val dotColor = if (active) telemetryColors.online else colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) telemetryColors.online.copy(alpha = 0.12f) else colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) colorScheme.onSurface else colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun XiaomiActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusText: String,
    statusPositive: Boolean?,
    onClick: () -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val telemetryColors = LocalTelemetryColors.current
    val badgeColor: Color = when (statusPositive) {
        true -> telemetryColors.online
        false -> telemetryColors.warning
        null -> colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusPositive == true) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                } else if (statusPositive == false) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "打开设置",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
