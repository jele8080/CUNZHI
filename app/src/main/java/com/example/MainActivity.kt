package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppTab
import com.example.ui.MonitorUiState
import com.example.ui.MonitorViewModel
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.ControlScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.PropertiesScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CunzhiMonitorApp(
                    onKeepScreenOnChanged = { keepOn ->
                        if (keepOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CunzhiMonitorApp(
    onKeepScreenOnChanged: (Boolean) -> Unit,
    viewModel: MonitorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh Xiaomi battery/notification health immediately when user returns from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBackgroundHealth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep screen on during active monitoring when enabled
    DisposableEffect(uiState.config.keepScreenOn) {
        onKeepScreenOnChanged(uiState.config.keepScreenOn)
        onDispose {
            onKeepScreenOnChanged(false)
        }
    }

    // Request POST_NOTIFICATIONS gracefully on Android 13+ for ForegroundService status notification
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Service continues cleanly whether granted or denied */ }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Back press on secondary tabs returns to Monitor tab cleanly
    BackHandler(enabled = uiState.activeTab != AppTab.MONITOR) {
        viewModel.selectTab(AppTab.MONITOR)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = uiState.activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    TabIcon(tab = tab, selected = selected, uiState = uiState)
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ActiveTabContent(uiState = uiState, viewModel = viewModel)
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.statusBars,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = uiState.activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    TabIcon(tab = tab, selected = selected, uiState = uiState)
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ActiveTabContent(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun TabIcon(
    tab: AppTab,
    selected: Boolean,
    uiState: MonitorUiState
) {
    val icon: ImageVector = when (tab) {
        AppTab.MONITOR -> if (selected) Icons.Rounded.MonitorHeart else Icons.Outlined.MonitorHeart
        AppTab.CONTROL -> if (selected) Icons.Rounded.SportsEsports else Icons.Outlined.SportsEsports
        AppTab.PROPERTIES -> if (selected) Icons.Rounded.Tune else Icons.Outlined.Tune
        AppTab.CONNECTION -> if (selected) Icons.Rounded.Dns else Icons.Outlined.Dns
    }

    if (tab == AppTab.MONITOR && uiState.onlineCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(uiState.onlineCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}

@Composable
private fun ActiveTabContent(
    uiState: MonitorUiState,
    viewModel: MonitorViewModel
) {
    when (uiState.activeTab) {
        AppTab.MONITOR -> MonitorScreen(
            uiState = uiState,
            onSelectDevice = viewModel::selectDevice,
            onToggleService = viewModel::toggleServiceRunning,
            onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
            onToggleChartAutoScale = viewModel::toggleChartAutoScale,
            onClearHistory = viewModel::clearSelectedDeviceHistory,
            onOpenProperties = { viewModel.selectTab(AppTab.PROPERTIES) },
            onOpenConnection = { viewModel.selectTab(AppTab.CONNECTION) }
        )

        AppTab.CONTROL -> ControlScreen(
            uiState = uiState,
            onSelectDevice = viewModel::selectDevice,
            onSetDownlinkFormat = viewModel::setDownlinkFormat,
            onWriteProperty = { mac, key, value -> viewModel.writeProperty(mac, key, value) },
            onQueryCoreFields = viewModel::queryCoreControlFields,
            onRunMotorPulse = viewModel::runMotorPulseTest,
            onStopMotor = viewModel::stopMotor,
            onRunStimPulse = viewModel::runStimPulseTest,
            onStopStim = viewModel::stopStimulation,
            onEmergencyStop = viewModel::emergencyStop,
            onOpenConnection = { viewModel.selectTab(AppTab.CONNECTION) }
        )

        AppTab.PROPERTIES -> PropertiesScreen(
            uiState = uiState,
            onSelectDevice = viewModel::selectDevice,
            onSearchQueryChange = viewModel::setPropertySearchQuery,
            onReadProperty = viewModel::readProperty,
            onWriteProperty = { mac, key, value -> viewModel.writeProperty(mac, key, value) }
        )

        AppTab.CONNECTION -> ConnectionScreen(
            uiState = uiState,
            onSaveAndRestart = viewModel::saveAndRestart,
            onToggleService = viewModel::toggleServiceRunning,
            onRefreshIps = viewModel::refreshLocalIps,
            onToggleKeepAliveLocks = viewModel::setKeepAliveLocksEnabled,
            onToggleAutoRecoverNetwork = viewModel::setAutoRecoverNetwork,
            onSelectLogFilter = viewModel::setLogFilter,
            onClearLogs = viewModel::clearLogs,
            onClearDevices = viewModel::clearAllDevices
        )
    }
}
