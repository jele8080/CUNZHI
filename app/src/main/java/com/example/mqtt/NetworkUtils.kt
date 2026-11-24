package com.example.mqtt

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    /**
     * Returns non-loopback local IPv4 addresses sorted with Wi-Fi / Hotspot interfaces first.
     */
    fun getLocalIpv4Addresses(): List<String> {
        val candidates = mutableListOf<Pair<Int, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                val priority = when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("ap") || name.startsWith("swlan") -> 1
                    name.startsWith("eth") -> 2
                    name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") -> 4
                    else -> 3
                }
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        candidates.add(priority to ip)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return candidates
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
    }
}
