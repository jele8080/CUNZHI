package com.example.mqtt

import android.content.Context
import android.net.wifi.WifiManager
import com.example.model.CunzhiProtocolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets

/**
 * Pure DNS/mDNS wire-format helper (RFC 6762) for responding to easysmart.local IPv4 A queries.
 * Kept static/pure so it can be deterministically unit tested on the JVM.
 */
object MdnsCodec {
    const val MDNS_MULTICAST_ADDR = "224.0.0.251"
    const val MDNS_PORT = 5353
    private const val TYPE_A = 1
    private const val TYPE_ANY = 255
    private const val CLASS_IN = 1

    fun isQueryForHost(packet: ByteArray, length: Int, targetHostname: String = CunzhiProtocolSchema.MDNS_HOST): Boolean {
        if (length < 12) return false
        val flags = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        val isResponse = (flags and 0x8000) != 0
        if (isResponse) return false

        val qdCount = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        if (qdCount <= 0) return false

        val normalizedTarget = targetHostname.trimEnd('.').lowercase()
        var offset = 12
        for (i in 0 until qdCount) {
            val (name, nextOffset) = readDnsName(packet, length, offset) ?: return false
            if (nextOffset + 4 > length) return false
            val qType = ((packet[nextOffset].toInt() and 0xFF) shl 8) or (packet[nextOffset + 1].toInt() and 0xFF)
            val qClass = (((packet[nextOffset + 2].toInt() and 0xFF) shl 8) or (packet[nextOffset + 3].toInt() and 0xFF)) and 0x7FFF
            offset = nextOffset + 4

            if (name.trimEnd('.').lowercase() == normalizedTarget &&
                (qType == TYPE_A || qType == TYPE_ANY) &&
                (qClass == CLASS_IN || qClass == TYPE_ANY)
            ) {
                return true
            }
        }
        return false
    }

    fun buildARecordResponse(
        hostname: String,
        ipv4Address: String,
        ttlSeconds: Int = 120
    ): ByteArray? {
        val ipBytes = parseIpv4Bytes(ipv4Address) ?: return null
        val out = ByteArrayOutputStream(64)

        // Header: ID=0, Flags=0x8400 (Response + Authoritative), QDCOUNT=0, ANCOUNT=1, NSCOUNT=0, ARCOUNT=0
        writeU16(out, 0x0000)
        writeU16(out, 0x8400)
        writeU16(out, 0x0000)
        writeU16(out, 0x0001)
        writeU16(out, 0x0000)
        writeU16(out, 0x0000)

        // Answer: NAME
        writeDnsName(out, hostname)
        // TYPE = A (1)
        writeU16(out, TYPE_A)
        // CLASS = IN (1) with cache-flush bit (0x8000) per RFC 6762
        writeU16(out, CLASS_IN or 0x8000)
        // TTL
        out.write((ttlSeconds ushr 24) and 0xFF)
        out.write((ttlSeconds ushr 16) and 0xFF)
        out.write((ttlSeconds ushr 8) and 0xFF)
        out.write(ttlSeconds and 0xFF)
        // RDLENGTH = 4
        writeU16(out, 4)
        // RDATA = IPv4 4 bytes
        out.write(ipBytes)

        return out.toByteArray()
    }

    fun parseIpv4Bytes(ipv4: String): ByteArray? {
        val parts = ipv4.trim().split('.')
        if (parts.size != 4) return null
        val result = ByteArray(4)
        for (i in 0..3) {
            val v = parts[i].toIntOrNull() ?: return null
            if (v !in 0..255) return null
            result[i] = v.toByte()
        }
        return result
    }

    private fun readDnsName(packet: ByteArray, length: Int, startOffset: Int): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var offset = startOffset
        var jumped = false
        var endOffset = startOffset
        var guard = 0

        while (offset < length && guard < 64) {
            guard++
            val len = packet[offset].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) endOffset = offset + 1
                return labels.joinToString(".") to endOffset
            }
            if ((len and 0xC0) == 0xC0) {
                if (offset + 1 >= length) return null
                val ptr = ((len and 0x3F) shl 8) or (packet[offset + 1].toInt() and 0xFF)
                if (!jumped) {
                    endOffset = offset + 2
                    jumped = true
                }
                offset = ptr
            } else {
                offset++
                if (offset + len > length) return null
                labels.add(String(packet, offset, len, StandardCharsets.UTF_8))
                offset += len
            }
        }
        return null
    }

    private fun writeDnsName(out: ByteArrayOutputStream, hostname: String) {
        val labels = hostname.trimEnd('.').split('.').filter { it.isNotEmpty() }
        for (label in labels) {
            val bytes = label.toByteArray(StandardCharsets.UTF_8)
            out.write(bytes.size and 0xFF)
            out.write(bytes)
        }
        out.write(0)
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}

/**
 * UDP 5353 mDNS responder that answers queries for easysmart.local with the Android phone's LAN IPv4.
 */
class MdnsResponder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onResponseSent: (Int) -> Unit,
    private val onLog: (title: String, detail: String, isError: Boolean) -> Unit
) {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var socket: MulticastSocket? = null
    private var listenJob: Job? = null
    private var responseCount = 0

    fun start(advertiseIp: String, hostname: String = CunzhiProtocolSchema.MDNS_HOST): Boolean {
        stop()
        val responseBytes = MdnsCodec.buildARecordResponse(hostname, advertiseIp)
        if (responseBytes == null) {
            onLog("mDNS 启动失败", "无效的 IPv4 地址: $advertiseIp", true)
            return false
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifiManager?.createMulticastLock("cunzhi-mdns-lock")
            lock?.setReferenceCounted(false)
            lock?.acquire()
            multicastLock = lock

            val group = InetAddress.getByName(MdnsCodec.MDNS_MULTICAST_ADDR)
            val ms = MulticastSocket(null)
            ms.reuseAddress = true
            ms.bind(InetSocketAddress(MdnsCodec.MDNS_PORT))
            ms.timeToLive = 255

            val targetInterface = findInterfaceForIp(advertiseIp)
            if (targetInterface != null) {
                try {
                    ms.networkInterface = targetInterface
                    ms.joinGroup(InetSocketAddress(group, MdnsCodec.MDNS_PORT), targetInterface)
                } catch (_: Exception) {
                    @Suppress("DEPRECATION")
                    ms.joinGroup(group)
                }
            } else {
                @Suppress("DEPRECATION")
                ms.joinGroup(group)
            }

            socket = ms
            responseCount = 0
            onLog("mDNS 宣告已开启", "$hostname -> $advertiseIp (UDP 5353)", false)

            listenJob = scope.launch(Dispatchers.IO) {
                // Send an initial unsolicited announcement so waiting devices discover immediately
                try {
                    ms.send(DatagramPacket(responseBytes, responseBytes.size, group, MdnsCodec.MDNS_PORT))
                } catch (_: Exception) {
                }

                val buffer = ByteArray(1500)
                while (isActive && !ms.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        ms.receive(packet)
                        if (MdnsCodec.isQueryForHost(packet.data, packet.length, hostname)) {
                            // Reply to multicast group per RFC 6762
                            ms.send(DatagramPacket(responseBytes, responseBytes.size, group, MdnsCodec.MDNS_PORT))
                            // Also unicast reply when query came from a non-5353 ephemeral port (QU / legacy resolver)
                            if (packet.port != MdnsCodec.MDNS_PORT && packet.address != null) {
                                try {
                                    ms.send(DatagramPacket(responseBytes, responseBytes.size, packet.address, packet.port))
                                } catch (_: Exception) {
                                }
                            }
                            responseCount++
                            onResponseSent(responseCount)
                            if (responseCount <= 5 || responseCount % 10 == 0) {
                                onLog(
                                    "已回应 mDNS 查询 #$responseCount",
                                    "来自 ${packet.address?.hostAddress ?: "LAN"} -> $hostname = $advertiseIp",
                                    false
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive && !ms.isClosed) {
                            onLog("mDNS 监听异常", e.message ?: e.javaClass.simpleName, true)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            onLog("mDNS 启动受限", e.message ?: "无法绑定 UDP 5353", true)
            stop()
            return false
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {
        }
        multicastLock = null
    }

    private fun findInterfaceForIp(ip: String): NetworkInterface? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && addr.hostAddress == ip) {
                        return iface
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
