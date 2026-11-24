package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.R
import java.util.Locale

data class BackgroundHealthStatus(
    val isXiaomiFamily: Boolean = false,
    val deviceDisplayName: String = "",
    val ignoringBatteryOptimizations: Boolean = false,
    val notificationsEnabled: Boolean = true
) {
    val allCoreChecksPassed: Boolean
        get() = ignoringBatteryOptimizations && notificationsEnabled
}

/**
 * Helper tailored for Xiaomi HyperOS / MIUI background service survival.
 * Uses only zero-permission system Settings / MIUI SecurityCenter intents with safe fallbacks.
 */
object XiaomiBackgroundHelper {

    private val xiaomiBrands = setOf("xiaomi", "redmi", "poco", "blackshark")

    fun isXiaomiFamilyDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.US)
        val brand = Build.BRAND.orEmpty().lowercase(Locale.US)
        return xiaomiBrands.any { manufacturer.contains(it) || brand.contains(it) }
    }

    fun inspectHealth(context: Context): BackgroundHealthStatus {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBattery = try {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (_: Exception) {
            false
        }
        val notificationsEnabled = try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: Exception) {
            true
        }
        val brand = Build.BRAND.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        val display = if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model".trim()

        return BackgroundHealthStatus(
            isXiaomiFamily = isXiaomiFamilyDevice(),
            deviceDisplayName = display,
            ignoringBatteryOptimizations = ignoringBattery,
            notificationsEnabled = notificationsEnabled
        )
    }

    /**
     * Opens Xiaomi PowerKeeper ("省电策略 -> 无限制") directly, falling back to Android battery optimization settings.
     */
    fun openBatteryStrategySettings(context: Context): Boolean {
        val appLabel = context.getString(R.string.app_name)
        val candidates = listOf(
            // Xiaomi HyperOS / MIUI per-app battery saver ("无限制")
            Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", appLabel)
            },
            // MIUI battery saver list fallback
            Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                )
            },
            // Standard Android battery optimization settings (zero restricted permissions)
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            // Final universal fallback: App details page
            appDetailsIntent(context)
        )
        return launchFirstAvailable(context, candidates)
    }

    /**
     * Opens Xiaomi Security Center Auto-Start ("自启动管理") directly, falling back to App Details.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            appDetailsIntent(context)
        )
        return launchFirstAvailable(context, candidates)
    }

    /**
     * Opens Xiaomi App Permissions ("权限管理", including background pop-up / Wi-Fi control) with safe fallback.
     */
    fun openXiaomiPermissions(context: Context): Boolean {
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
            },
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            },
            appDetailsIntent(context)
        )
        return launchFirstAvailable(context, candidates)
    }

    /**
     * Opens Android App Notification Settings so user can ensure ForegroundService notification is allowed.
     */
    fun openNotificationSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
            appDetailsIntent(context)
        )
        return launchFirstAvailable(context, candidates)
    }

    private fun appDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    private fun launchFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try next fallback intent
            }
        }
        return false
    }
}
