package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MonitorRepository
import com.example.model.ConnectionStatus
import com.example.model.CunzhiProtocolSchema
import com.example.model.DeviceSnapshot
import com.example.model.DownlinkFormat
import com.example.model.DownlinkResult
import com.example.model.LogCategory
import com.example.model.MonitorConfig
import com.example.model.MonitorMode
import com.example.model.ProtocolLogEntry
import com.example.model.RuntimeStatus
import com.example.service.BackgroundHealthStatus
import com.example.service.MqttMonitorService
import com.example.service.XiaomiBackgroundHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    MONITOR("监看"),
    CONTROL("控制"),
    PROPERTIES("属性"),
    CONNECTION("连接")
}

data class ControlTestState(
    val motorPulseRemainingMs: Long = 0L,
    val motorPulseTotalMs: Long = 0L,
    val stimPulseActive: Boolean = false,
    val lastDownlinkResult: DownlinkResult? = null
)

data class MonitorUiState(
    val activeTab: AppTab = AppTab.MONITOR,
    val config: MonitorConfig = MonitorConfig(),
    val runtime: RuntimeStatus = RuntimeStatus(),
    val devices: List<DeviceSnapshot> = emptyList(),
    val selectedMac: String? = null,
    val logs: List<ProtocolLogEntry> = emptyList(),
    val logFilter: LogCategory? = null,
    val propertySearchQuery: String = "",
    val autoScaleChart: Boolean = false,
    val backgroundHealth: BackgroundHealthStatus = BackgroundHealthStatus(),
    val controlTest: ControlTestState = ControlTestState(),
    val nowMs: Long = System.currentTimeMillis()
) {
    val selectedDevice: DeviceSnapshot?
        get() = devices.firstOrNull { it.mac == selectedMac } ?: devices.firstOrNull()

    val onlineCount: Int
        get() = devices.count { it.online }

    val filteredLogs: List<ProtocolLogEntry>
        get() = if (logFilter == null) logs else logs.filter { it.category == logFilter }
}

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MonitorRepository.getInstance(application)

    private val activeTab = MutableStateFlow(AppTab.MONITOR)
    private val selectedMac = MutableStateFlow<String?>(null)
    private val logFilter = MutableStateFlow<LogCategory?>(null)
    private val propertySearchQuery = MutableStateFlow("")
    private val autoScaleChart = MutableStateFlow(false)
    private val backgroundHealth = MutableStateFlow(XiaomiBackgroundHelper.inspectHealth(application))
    private val controlTestState = MutableStateFlow(ControlTestState())
    private val clockTickMs = MutableStateFlow(System.currentTimeMillis())

    private var motorPulseJob: Job? = null
    private var stimPulseJob: Job? = null

    private val savedConfig = repository.configStore.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MonitorConfig()
    )

    val uiState: StateFlow<MonitorUiState> = combine(
        combine(
            activeTab,
            savedConfig,
            repository.runtimeStatus,
            repository.telemetryEngine.records,
            clockTickMs
        ) { tab, config, runtime, _, now ->
            val snapshots = repository.telemetryEngine.computeSnapshots(now)
            BaseSnapshotBundle(tab, config, runtime, snapshots, now)
        },
        combine(
            selectedMac,
            repository.telemetryEngine.logs,
            logFilter,
            propertySearchQuery,
            autoScaleChart
        ) { mac, logs, filter, query, autoScale ->
            UiControlsBundle(mac, logs, filter, query, autoScale)
        },
        backgroundHealth,
        controlTestState
    ) { base, controls, health, testState ->
        val effectiveMac = if (controls.selectedMac != null && base.snapshots.any { it.mac == controls.selectedMac }) {
            controls.selectedMac
        } else {
            base.snapshots.firstOrNull()?.mac
        }
        MonitorUiState(
            activeTab = base.tab,
            config = base.config,
            runtime = base.runtime,
            devices = base.snapshots,
            selectedMac = effectiveMac,
            logs = controls.logs,
            logFilter = controls.logFilter,
            propertySearchQuery = controls.propertySearchQuery,
            autoScaleChart = controls.autoScaleChart,
            backgroundHealth = health,
            controlTest = testState,
            nowMs = base.nowMs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MonitorUiState(
            backgroundHealth = XiaomiBackgroundHelper.inspectHealth(application)
        )
    )

    init {
        viewModelScope.launch {
            repository.refreshLocalIps()
            val config = repository.configStore.loadCurrent()
            if (repository.runtimeStatus.value.status == ConnectionStatus.STOPPED) {
                repository.start(config)
                MqttMonitorService.startService(getApplication())
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(500L)
                clockTickMs.value = System.currentTimeMillis()
            }
        }
    }

    fun selectTab(tab: AppTab) {
        activeTab.value = tab
    }

    fun selectDevice(mac: String) {
        selectedMac.value = mac
    }

    fun setLogFilter(category: LogCategory?) {
        logFilter.value = category
    }

    fun setPropertySearchQuery(query: String) {
        propertySearchQuery.value = query
    }

    fun toggleChartAutoScale() {
        autoScaleChart.value = !autoScaleChart.value
    }

    fun refreshBackgroundHealth() {
        backgroundHealth.value = XiaomiBackgroundHelper.inspectHealth(getApplication())
        repository.refreshLocalIps()
    }

    fun toggleKeepScreenOn() {
        viewModelScope.launch {
            val next = !savedConfig.value.keepScreenOn
            repository.configStore.updateKeepScreenOn(next)
        }
    }

    fun setKeepAliveLocksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.configStore.updateKeepAliveLocks(enabled)
        }
    }

    fun setAutoRecoverNetwork(enabled: Boolean) {
        viewModelScope.launch {
            repository.configStore.updateAutoRecoverNetwork(enabled)
            repository.updateAutoRecoverPreference(enabled)
        }
    }

    fun setDownlinkFormat(format: DownlinkFormat) {
        viewModelScope.launch {
            repository.configStore.updateDownlinkFormat(format)
        }
    }

    fun refreshLocalIps() {
        repository.refreshLocalIps()
    }

    fun toggleServiceRunning() {
        val currentStatus = repository.runtimeStatus.value.status
        if (currentStatus == ConnectionStatus.STOPPED || currentStatus == ConnectionStatus.ERROR) {
            val config = savedConfig.value
            repository.start(config)
            MqttMonitorService.startService(getApplication())
        } else {
            cancelActiveTests()
            repository.stop()
            MqttMonitorService.stopService(getApplication())
        }
    }

    fun saveAndRestart(newConfig: MonitorConfig) {
        viewModelScope.launch {
            repository.configStore.save(newConfig)
            repository.start(newConfig)
            MqttMonitorService.startService(getApplication())
        }
    }

    // ---- Downlink Read / Write / Hardware Test Operations ----

    fun readProperty(mac: String, key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.sendGetCommand(mac, key)
            controlTestState.value = controlTestState.value.copy(lastDownlinkResult = res)
        }
    }

    fun writeProperty(mac: String, key: String, value: Any, readBackAfter: Boolean = true) {
        val sanitizedValue: Any = when (key) {
            "safe", "shock" -> ((value as? Number)?.toInt() ?: 0).coerceIn(0, 1)
            "delay" -> ((value as? Number)?.toInt() ?: CunzhiProtocolSchema.MIN_STIM_DELAY_MS)
                .coerceIn(CunzhiProtocolSchema.MIN_STIM_DELAY_MS, CunzhiProtocolSchema.MAX_STIM_DELAY_MS)
            "voltage", "game_e_vol" -> ((value as? Number)?.toInt() ?: 0)
                .coerceIn(0, CunzhiProtocolSchema.UNSAFE_VOLTAGE_LIMIT_V)
            else -> value
        }
        viewModelScope.launch(Dispatchers.IO) {
            val format = savedConfig.value.downlinkFormat
            val res = repository.sendSetCommand(mac, key, sanitizedValue, format)
            controlTestState.value = controlTestState.value.copy(lastDownlinkResult = res)
            if (res.success && readBackAfter) {
                delay(220L)
                repository.sendGetCommand(mac, key)
            }
        }
    }

    fun queryCoreControlFields(mac: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val keys = listOf("power", "voltage", "shock", "delay", "safe", "report_delay_ms")
            var last: DownlinkResult? = null
            for (k in keys) {
                last = repository.sendGetCommand(mac, k)
                delay(70L)
            }
            if (last != null) {
                controlTestState.value = controlTestState.value.copy(lastDownlinkResult = last)
            }
        }
    }

    fun runMotorPulseTest(mac: String, powerLevel: Int, durationMs: Long) {
        motorPulseJob?.cancel()
        motorPulseJob = viewModelScope.launch(Dispatchers.IO) {
            val format = savedConfig.value.downlinkFormat
            val startRes = repository.sendSetCommand(mac, "power", powerLevel, format)
            controlTestState.value = controlTestState.value.copy(
                motorPulseTotalMs = if (startRes.success) durationMs else 0L,
                motorPulseRemainingMs = if (startRes.success) durationMs else 0L,
                lastDownlinkResult = startRes
            )
            if (!startRes.success) return@launch

            var remaining = durationMs
            while (remaining > 0L && isActive) {
                val step = minOf(100L, remaining)
                delay(step)
                remaining -= step
                controlTestState.value = controlTestState.value.copy(motorPulseRemainingMs = remaining)
            }
            val stopRes = repository.sendSetCommand(mac, "power", 0, format)
            controlTestState.value = controlTestState.value.copy(
                motorPulseRemainingMs = 0L,
                motorPulseTotalMs = 0L,
                lastDownlinkResult = stopRes
            )
        }
    }

    fun stopMotor(mac: String) {
        motorPulseJob?.cancel()
        motorPulseJob = null
        viewModelScope.launch(Dispatchers.IO) {
            val format = savedConfig.value.downlinkFormat
            val res = repository.sendSetCommand(mac, "power", 0, format)
            controlTestState.value = controlTestState.value.copy(
                motorPulseRemainingMs = 0L,
                motorPulseTotalMs = 0L,
                lastDownlinkResult = res
            )
        }
    }

    fun runStimPulseTest(
        mac: String,
        voltage: Int,
        delayVal: Int,
        shockLevel: Int,
        autoResetAfterMs: Long
    ) {
        val safeVoltage = voltage.coerceIn(0, CunzhiProtocolSchema.UNSAFE_VOLTAGE_LIMIT_V)
        val safeDelay = delayVal.coerceIn(
            CunzhiProtocolSchema.MIN_STIM_DELAY_MS,
            CunzhiProtocolSchema.MAX_STIM_DELAY_MS
        )
        val safeShock = shockLevel.coerceIn(0, 1)
        stimPulseJob?.cancel()
        stimPulseJob = viewModelScope.launch(Dispatchers.IO) {
            val format = savedConfig.value.downlinkFormat
            controlTestState.value = controlTestState.value.copy(stimPulseActive = true)
            try {
                val rVolt = repository.sendSetCommand(mac, "voltage", safeVoltage, format)
                if (!rVolt.success) {
                    controlTestState.value = controlTestState.value.copy(
                        stimPulseActive = false,
                        lastDownlinkResult = rVolt
                    )
                    return@launch
                }
                delay(60L)
                repository.sendSetCommand(mac, "delay", safeDelay, format)
                delay(60L)
                val rShock = repository.sendSetCommand(mac, "shock", safeShock, format)
                controlTestState.value = controlTestState.value.copy(lastDownlinkResult = rShock)

                if (rShock.success && autoResetAfterMs > 0L) {
                    delay(autoResetAfterMs)
                    val rOff = repository.sendSetCommand(mac, "shock", 0, format)
                    controlTestState.value = controlTestState.value.copy(lastDownlinkResult = rOff)
                }
            } finally {
                controlTestState.value = controlTestState.value.copy(stimPulseActive = false)
            }
        }
    }

    fun stopStimulation(mac: String) {
        stimPulseJob?.cancel()
        stimPulseJob = null
        viewModelScope.launch(Dispatchers.IO) {
            val format = savedConfig.value.downlinkFormat
            repository.sendSetCommand(mac, "shock", 0, format)
            val res = repository.sendSetCommand(mac, "voltage", 0, format)
            controlTestState.value = controlTestState.value.copy(
                stimPulseActive = false,
                lastDownlinkResult = res
            )
        }
    }

    fun emergencyStop(mac: String) {
        cancelActiveTests()
        viewModelScope.launch(Dispatchers.IO) {
            val format = savedConfig.value.downlinkFormat
            val res = repository.sendEmergencyStop(mac, format)
            controlTestState.value = controlTestState.value.copy(
                motorPulseRemainingMs = 0L,
                motorPulseTotalMs = 0L,
                stimPulseActive = false,
                lastDownlinkResult = res
            )
        }
    }

    private fun cancelActiveTests() {
        motorPulseJob?.cancel()
        motorPulseJob = null
        stimPulseJob?.cancel()
        stimPulseJob = null
    }

    fun clearSelectedDeviceHistory() {
        val mac = uiState.value.selectedDevice?.mac
        repository.telemetryEngine.clearHistory(mac)
    }

    fun clearAllDevices() {
        repository.telemetryEngine.clearDevices()
        selectedMac.value = null
    }

    fun clearLogs() {
        repository.telemetryEngine.clearLogs()
    }

    override fun onCleared() {
        cancelActiveTests()
        super.onCleared()
    }

    private data class BaseSnapshotBundle(
        val tab: AppTab,
        val config: MonitorConfig,
        val runtime: RuntimeStatus,
        val snapshots: List<DeviceSnapshot>,
        val nowMs: Long
    )

    private data class UiControlsBundle(
        val selectedMac: String?,
        val logs: List<ProtocolLogEntry>,
        val logFilter: LogCategory?,
        val propertySearchQuery: String,
        val autoScaleChart: Boolean
    )
}
