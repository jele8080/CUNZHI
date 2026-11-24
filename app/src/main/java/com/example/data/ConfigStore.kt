package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CunzhiProtocolSchema
import com.example.model.DownlinkFormat
import com.example.model.MonitorConfig
import com.example.model.MonitorMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.monitorDataStore by preferencesDataStore(name = "cunzhi_monitor_prefs")

class ConfigStore(private val context: Context) {
    private object Keys {
        val MODE = stringPreferencesKey("mode")
        val EMBEDDED_PORT = intPreferencesKey("embedded_port")
        val MDNS_ENABLED = booleanPreferencesKey("mdns_enabled")
        val MDNS_IP_OVERRIDE = stringPreferencesKey("mdns_ip_override")
        val EXTERNAL_URL = stringPreferencesKey("external_url")
        val EXTERNAL_USER = stringPreferencesKey("external_user")
        val EXTERNAL_PASS = stringPreferencesKey("external_pass")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEEP_ALIVE_LOCKS = booleanPreferencesKey("keep_alive_locks")
        val AUTO_RECOVER_NETWORK = booleanPreferencesKey("auto_recover_network")
        val DOWNLINK_FORMAT = stringPreferencesKey("downlink_format")
    }

    val configFlow: Flow<MonitorConfig> = context.monitorDataStore.data.map { prefs ->
        val mode = when (prefs[Keys.MODE]) {
            MonitorMode.EXTERNAL.apiName -> MonitorMode.EXTERNAL
            else -> MonitorMode.EMBEDDED
        }
        val format = when (prefs[Keys.DOWNLINK_FORMAT]) {
            DownlinkFormat.FLAT.apiName -> DownlinkFormat.FLAT
            else -> DownlinkFormat.KEY_VALUE
        }
        MonitorConfig(
            mode = mode,
            embeddedPort = prefs[Keys.EMBEDDED_PORT] ?: CunzhiProtocolSchema.DEFAULT_MQTT_PORT,
            mdnsEnabled = prefs[Keys.MDNS_ENABLED] ?: true,
            mdnsIpOverride = prefs[Keys.MDNS_IP_OVERRIDE] ?: "",
            externalBrokerUrl = prefs[Keys.EXTERNAL_URL] ?: "mqtt://192.168.1.100:1883",
            externalUsername = prefs[Keys.EXTERNAL_USER] ?: "",
            externalPassword = prefs[Keys.EXTERNAL_PASS] ?: "",
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            keepAliveLocksEnabled = prefs[Keys.KEEP_ALIVE_LOCKS] ?: true,
            autoRecoverNetwork = prefs[Keys.AUTO_RECOVER_NETWORK] ?: true,
            downlinkFormat = format
        )
    }

    suspend fun loadCurrent(): MonitorConfig = configFlow.first()

    suspend fun save(config: MonitorConfig) {
        context.monitorDataStore.edit { prefs ->
            prefs[Keys.MODE] = config.mode.apiName
            prefs[Keys.EMBEDDED_PORT] = config.embeddedPort
            prefs[Keys.MDNS_ENABLED] = config.mdnsEnabled
            prefs[Keys.MDNS_IP_OVERRIDE] = config.mdnsIpOverride.trim()
            prefs[Keys.EXTERNAL_URL] = config.externalBrokerUrl.trim()
            prefs[Keys.EXTERNAL_USER] = config.externalUsername
            prefs[Keys.EXTERNAL_PASS] = config.externalPassword
            prefs[Keys.KEEP_SCREEN_ON] = config.keepScreenOn
            prefs[Keys.KEEP_ALIVE_LOCKS] = config.keepAliveLocksEnabled
            prefs[Keys.AUTO_RECOVER_NETWORK] = config.autoRecoverNetwork
            prefs[Keys.DOWNLINK_FORMAT] = config.downlinkFormat.apiName
        }
    }

    suspend fun updateKeepScreenOn(enabled: Boolean) {
        context.monitorDataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun updateKeepAliveLocks(enabled: Boolean) {
        context.monitorDataStore.edit { prefs ->
            prefs[Keys.KEEP_ALIVE_LOCKS] = enabled
        }
    }

    suspend fun updateAutoRecoverNetwork(enabled: Boolean) {
        context.monitorDataStore.edit { prefs ->
            prefs[Keys.AUTO_RECOVER_NETWORK] = enabled
        }
    }

    suspend fun updateDownlinkFormat(format: DownlinkFormat) {
        context.monitorDataStore.edit { prefs ->
            prefs[Keys.DOWNLINK_FORMAT] = format.apiName
        }
    }
}
