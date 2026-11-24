package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.MonitorRepository
import com.example.model.ConnectionStatus
import com.example.model.LogCategory
import com.example.model.MonitorMode
import com.example.model.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MqttMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private var lockPolicyJob: Job? = null

    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = MonitorRepository.getInstance(applicationContext)
        when (intent?.action) {
            ACTION_STOP -> {
                releaseKeepAliveLocks(repository)
                repository.stop()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                // If MIUI/HyperOS killed and resurrected the service process (intent == null), restore config automatically
                if (intent == null) {
                    repository.ensureStartedFromPersistedConfig()
                }

                val initialNotification = buildNotification(repository.runtimeStatus.value, 0)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            initialNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, initialNotification)
                    }
                } catch (_: Exception) {
                    // Graceful fallback if OS restricts foreground promotion
                }

                observeKeepAliveLockPolicy(repository)
                observeNotificationUpdates(repository)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val repository = MonitorRepository.getInstance(applicationContext)
        if (repository.runtimeStatus.value.status != ConnectionStatus.STOPPED) {
            repository.telemetryEngine.recordLog(
                LogCategory.SYSTEM,
                "应用已切至后台继续运行",
                "已启用 stopWithTask=false 保持 MQTT 监听；在小米手机上建议锁定最近任务卡片以防一键清理"
            )
        }
    }

    /**
     * Android 14+ (API 34+) / Xiaomi HyperOS enforces a 6-hour dataSync FGS window.
     * Handling onTimeout prevents RemoteServiceException crashes during marathon monitoring sessions.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val repository = MonitorRepository.getInstance(applicationContext)
        repository.telemetryEngine.recordLog(
            LogCategory.SYSTEM,
            "触发系统 dataSync 前台服务时长上限",
            "已安全退出前台通知状态，打开 App 可随时继续监看"
        )
        releaseKeepAliveLocks(repository)
        stopForegroundCompat()
        stopSelf(startId)
    }

    private fun observeKeepAliveLockPolicy(repository: MonitorRepository) {
        lockPolicyJob?.cancel()
        lockPolicyJob = serviceScope.launch {
            combine(
                repository.configStore.configFlow,
                repository.runtimeStatus
            ) { config, runtime ->
                config.keepAliveLocksEnabled && runtime.status != ConnectionStatus.STOPPED
            }.collect { shouldHoldLocks ->
                if (shouldHoldLocks) {
                    acquireKeepAliveLocks(repository)
                } else {
                    releaseKeepAliveLocks(repository)
                }
            }
        }
    }

    private fun acquireKeepAliveLocks(repository: MonitorRepository) {
        var wakeHeld = cpuWakeLock?.isHeld == true
        if (!wakeHeld) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CunzhiMonitor::MqttKeepAlive")
                wl?.setReferenceCounted(false)
                // Acquire in bounded 12-hour chunks to stay safe and tidy
                wl?.acquire(12 * 60 * 60 * 1000L)
                cpuWakeLock = wl
                wakeHeld = wl?.isHeld == true
            } catch (_: Exception) {
                wakeHeld = false
            }
        }

        var wifiHeld = wifiLock?.isHeld == true
        if (!wifiHeld) {
            try {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                val wl = wm?.createWifiLock(lockMode, "CunzhiMonitor::WifiLowLatency")
                wl?.setReferenceCounted(false)
                wl?.acquire()
                wifiLock = wl
                wifiHeld = wl?.isHeld == true
            } catch (_: Exception) {
                wifiHeld = false
            }
        }

        repository.updateLockStatus(wakeLockActive = wakeHeld, wifiLockActive = wifiHeld)
    }

    private fun releaseKeepAliveLocks(repository: MonitorRepository) {
        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
        } catch (_: Exception) {
        }
        cpuWakeLock = null

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {
        }
        wifiLock = null

        repository.updateLockStatus(wakeLockActive = false, wifiLockActive = false)
    }

    private fun observeNotificationUpdates(repository: MonitorRepository) {
        notificationJob?.cancel()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationJob = serviceScope.launch {
            combine(
                repository.runtimeStatus,
                repository.telemetryEngine.records
            ) { status, records ->
                val onlineCount = repository.telemetryEngine.computeSnapshots().count { it.online }
                status to (onlineCount to records.size)
            }.collect { (status, counts) ->
                if (status.status == ConnectionStatus.STOPPED) {
                    releaseKeepAliveLocks(repository)
                    stopForegroundCompat()
                    stopSelf()
                } else {
                    try {
                        manager.notify(NOTIFICATION_ID, buildNotification(status, counts.first))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun buildNotification(status: RuntimeStatus, onlineCount: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MqttMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (status.mode) {
            MonitorMode.EMBEDDED -> "内置 Broker 运行中 (:${status.brokerPort ?: 1883})"
            MonitorMode.EXTERNAL -> "外部 Broker ${status.status.label}"
        }

        val lockBadge = if (status.wakeLockActive || status.wifiLockActive) " · 防休眠锁生效" else ""
        val text = when (status.mode) {
            MonitorMode.EMBEDDED -> {
                val mdnsPart = if (status.mdnsActive && status.discoveryIp != null) {
                    "easysmart.local -> ${status.discoveryIp}"
                } else {
                    status.activeEndpoint
                }
                "$mdnsPart · $onlineCount 台在线$lockBadge"
            }
            MonitorMode.EXTERNAL -> "${status.activeEndpoint} · $onlineCount 台在线$lockBadge"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.action_stop), stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        val repository = MonitorRepository.getInstance(applicationContext)
        releaseKeepAliveLocks(repository)
        lockPolicyJob?.cancel()
        notificationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "cunzhi_mqtt_monitor"
        private const val NOTIFICATION_ID = 1883
        const val ACTION_START = "com.example.action.START_MONITOR"
        const val ACTION_STOP = "com.example.action.STOP_MONITOR"

        fun startService(context: Context) {
            val intent = Intent(context, MqttMonitorService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // In-app repository still runs even if foreground service start is deferred by OS
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MqttMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
