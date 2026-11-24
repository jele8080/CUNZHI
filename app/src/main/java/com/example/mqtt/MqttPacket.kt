package com.example.mqtt

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

sealed class MqttPacket {
    data class Connect(
        val protocolName: String,
        val protocolLevel: Int,
        val cleanSession: Boolean,
        val keepAliveSec: Int,
        val clientId: String,
        val username: String? = null,
        val password: String? = null
    ) : MqttPacket()

    data class ConnAck(
        val sessionPresent: Boolean,
        val returnCode: Int
    ) : MqttPacket()

    data class Publish(
        val topic: String,
        val payload: ByteArray,
        val qos: Int = 0,
        val retain: Boolean = false,
        val dup: Boolean = false,
        val packetId: Int? = null
    ) : MqttPacket() {
        fun payloadUtf8(): String = String(payload, StandardCharsets.UTF_8)
    }

    data class PubAck(val packetId: Int) : MqttPacket()
    data class PubRec(val packetId: Int) : MqttPacket()
    data class PubRel(val packetId: Int) : MqttPacket()
    data class PubComp(val packetId: Int) : MqttPacket()

    data class Subscribe(
        val packetId: Int,
        val subscriptions: List<Pair<String, Int>>
    ) : MqttPacket()

    data class SubAck(
        val packetId: Int,
        val returnCodes: List<Int>
    ) : MqttPacket()

    data class Unsubscribe(
        val packetId: Int,
        val topics: List<String>
    ) : MqttPacket()

    data class UnsubAck(val packetId: Int) : MqttPacket()
    data object PingReq : MqttPacket()
    data object PingResp : MqttPacket()
    data object Disconnect : MqttPacket()
}

object MqttCodec {
    private const val TYPE_CONNECT = 1
    private const val TYPE_CONNACK = 2
    private const val TYPE_PUBLISH = 3
    private const val TYPE_PUBACK = 4
    private const val TYPE_PUBREC = 5
    private const val TYPE_PUBREL = 6
    private const val TYPE_PUBCOMP = 7
    private const val TYPE_SUBSCRIBE = 8
    private const val TYPE_SUBACK = 9
    private const val TYPE_UNSUBSCRIBE = 10
    private const val TYPE_UNSUBACK = 11
    private const val TYPE_PINGREQ = 12
    private const val TYPE_PINGRESP = 13
    private const val TYPE_DISCONNECT = 14

    private const val MAX_PACKET_SIZE = 256 * 1024

    fun readPacket(input: InputStream): MqttPacket {
        val header = input.read()
        if (header == -1) throw EOFException("Stream closed")

        val packetType = (header ushr 4) and 0x0F
        val flags = header and 0x0F
        val remainingLength = readRemainingLength(input)
        if (remainingLength < 0 || remainingLength > MAX_PACKET_SIZE) {
            throw IOException("Invalid MQTT remaining length: $remainingLength")
        }

        val body = ByteArray(remainingLength)
        if (remainingLength > 0) {
            DataInputStream(input).readFully(body)
        }

        val reader = ByteReader(body)
        return when (packetType) {
            TYPE_CONNECT -> decodeConnect(reader)
            TYPE_CONNACK -> {
                val flagsByte = reader.readU8()
                val rc = reader.readU8()
                MqttPacket.ConnAck(sessionPresent = (flagsByte and 0x01) != 0, returnCode = rc)
            }
            TYPE_PUBLISH -> decodePublish(flags, reader)
            TYPE_PUBACK -> MqttPacket.PubAck(reader.readU16())
            TYPE_PUBREC -> MqttPacket.PubRec(reader.readU16())
            TYPE_PUBREL -> MqttPacket.PubRel(reader.readU16())
            TYPE_PUBCOMP -> MqttPacket.PubComp(reader.readU16())
            TYPE_SUBSCRIBE -> decodeSubscribe(reader)
            TYPE_SUBACK -> {
                val packetId = reader.readU16()
                val codes = mutableListOf<Int>()
                while (reader.remaining() > 0) {
                    codes.add(reader.readU8())
                }
                MqttPacket.SubAck(packetId, codes)
            }
            TYPE_UNSUBSCRIBE -> {
                val packetId = reader.readU16()
                val topics = mutableListOf<String>()
                while (reader.remaining() > 0) {
                    topics.add(reader.readUtf8())
                }
                MqttPacket.Unsubscribe(packetId, topics)
            }
            TYPE_UNSUBACK -> MqttPacket.UnsubAck(reader.readU16())
            TYPE_PINGREQ -> MqttPacket.PingReq
            TYPE_PINGRESP -> MqttPacket.PingResp
            TYPE_DISCONNECT -> MqttPacket.Disconnect
            else -> throw IOException("Unsupported MQTT packet type: $packetType")
        }
    }

    fun writePacket(output: OutputStream, packet: MqttPacket) {
        val bytes = encodePacket(packet)
        output.write(bytes)
        output.flush()
    }

    fun encodePacket(packet: MqttPacket): ByteArray {
        val body = ByteArrayOutputStream()
        val fixedHeader: Int = when (packet) {
            is MqttPacket.Connect -> {
                writeUtf8(body, packet.protocolName)
                body.write(packet.protocolLevel and 0xFF)
                var connectFlags = 0
                if (packet.cleanSession) connectFlags = connectFlags or 0x02
                if (!packet.username.isNullOrEmpty()) connectFlags = connectFlags or 0x80
                if (!packet.password.isNullOrEmpty()) connectFlags = connectFlags or 0x40
                body.write(connectFlags)
                writeU16(body, packet.keepAliveSec)
                writeUtf8(body, packet.clientId)
                if (!packet.username.isNullOrEmpty()) writeUtf8(body, packet.username)
                if (!packet.password.isNullOrEmpty()) writeUtf8(body, packet.password)
                TYPE_CONNECT shl 4
            }
            is MqttPacket.ConnAck -> {
                body.write(if (packet.sessionPresent) 1 else 0)
                body.write(packet.returnCode and 0xFF)
                TYPE_CONNACK shl 4
            }
            is MqttPacket.Publish -> {
                writeUtf8(body, packet.topic)
                if (packet.qos > 0) {
                    writeU16(body, packet.packetId ?: 1)
                }
                body.write(packet.payload)
                var flags = 0
                if (packet.dup) flags = flags or 0x08
                flags = flags or ((packet.qos and 0x03) shl 1)
                if (packet.retain) flags = flags or 0x01
                (TYPE_PUBLISH shl 4) or flags
            }
            is MqttPacket.PubAck -> {
                writeU16(body, packet.packetId)
                TYPE_PUBACK shl 4
            }
            is MqttPacket.PubRec -> {
                writeU16(body, packet.packetId)
                TYPE_PUBREC shl 4
            }
            is MqttPacket.PubRel -> {
                writeU16(body, packet.packetId)
                (TYPE_PUBREL shl 4) or 0x02
            }
            is MqttPacket.PubComp -> {
                writeU16(body, packet.packetId)
                TYPE_PUBCOMP shl 4
            }
            is MqttPacket.Subscribe -> {
                writeU16(body, packet.packetId)
                for ((filter, qos) in packet.subscriptions) {
                    writeUtf8(body, filter)
                    body.write(qos and 0x03)
                }
                (TYPE_SUBSCRIBE shl 4) or 0x02
            }
            is MqttPacket.SubAck -> {
                writeU16(body, packet.packetId)
                for (code in packet.returnCodes) {
                    body.write(code and 0xFF)
                }
                TYPE_SUBACK shl 4
            }
            is MqttPacket.Unsubscribe -> {
                writeU16(body, packet.packetId)
                for (topic in packet.topics) {
                    writeUtf8(body, topic)
                }
                (TYPE_UNSUBSCRIBE shl 4) or 0x02
            }
            is MqttPacket.UnsubAck -> {
                writeU16(body, packet.packetId)
                TYPE_UNSUBACK shl 4
            }
            MqttPacket.PingReq -> TYPE_PINGREQ shl 4
            MqttPacket.PingResp -> TYPE_PINGRESP shl 4
            MqttPacket.Disconnect -> TYPE_DISCONNECT shl 4
        }

        val bodyBytes = body.toByteArray()
        val out = ByteArrayOutputStream(bodyBytes.size + 5)
        out.write(fixedHeader)
        writeRemainingLength(out, bodyBytes.size)
        out.write(bodyBytes)
        return out.toByteArray()
    }

    fun topicMatchesFilter(topic: String, filter: String): Boolean {
        if (filter == "#" || filter == topic) return true
        val topicParts = topic.split('/')
        val filterParts = filter.split('/')
        var i = 0
        while (i < filterParts.size) {
            val f = filterParts[i]
            if (f == "#") return true
            if (i >= topicParts.size) return false
            if (f != "+" && f != topicParts[i]) return false
            i++
        }
        return i == topicParts.size
    }

    private fun decodeConnect(reader: ByteReader): MqttPacket.Connect {
        val protocolName = reader.readUtf8()
        val protocolLevel = reader.readU8()
        val connectFlags = reader.readU8()
        val cleanSession = (connectFlags and 0x02) != 0
        val willFlag = (connectFlags and 0x04) != 0
        val passwordFlag = (connectFlags and 0x40) != 0
        val usernameFlag = (connectFlags and 0x80) != 0
        val keepAlive = reader.readU16()
        val clientId = reader.readUtf8()

        if (willFlag) {
            reader.readUtf8() // will topic
            reader.readBytesPrefixed() // will message
        }
        val username = if (usernameFlag && reader.remaining() > 0) reader.readUtf8() else null
        val password = if (passwordFlag && reader.remaining() > 0) {
            String(reader.readBytesPrefixed(), StandardCharsets.UTF_8)
        } else null

        return MqttPacket.Connect(
            protocolName = protocolName,
            protocolLevel = protocolLevel,
            cleanSession = cleanSession,
            keepAliveSec = keepAlive,
            clientId = clientId,
            username = username,
            password = password
        )
    }

    private fun decodePublish(flags: Int, reader: ByteReader): MqttPacket.Publish {
        val dup = (flags and 0x08) != 0
        val qos = (flags ushr 1) and 0x03
        val retain = (flags and 0x01) != 0
        val topic = reader.readUtf8()
        val packetId = if (qos > 0) reader.readU16() else null
        val payload = reader.readRemainingBytes()
        return MqttPacket.Publish(
            topic = topic,
            payload = payload,
            qos = qos,
            retain = retain,
            dup = dup,
            packetId = packetId
        )
    }

    private fun decodeSubscribe(reader: ByteReader): MqttPacket.Subscribe {
        val packetId = reader.readU16()
        val subs = mutableListOf<Pair<String, Int>>()
        while (reader.remaining() > 0) {
            val filter = reader.readUtf8()
            val qos = reader.readU8() and 0x03
            subs.add(filter to qos)
        }
        return MqttPacket.Subscribe(packetId, subs)
    }

    private fun readRemainingLength(input: InputStream): Int {
        var multiplier = 1
        var value = 0
        var count = 0
        while (true) {
            val encodedByte = input.read()
            if (encodedByte == -1) throw EOFException("Unexpected EOF reading remaining length")
            value += (encodedByte and 0x7F) * multiplier
            if ((encodedByte and 0x80) == 0) break
            multiplier *= 128
            count++
            if (count > 3) throw IOException("Malformed MQTT remaining length")
        }
        return value
    }

    private fun writeRemainingLength(out: OutputStream, length: Int) {
        var x = length
        do {
            var encodedByte = x % 128
            x /= 128
            if (x > 0) encodedByte = encodedByte or 0x80
            out.write(encodedByte)
        } while (x > 0)
    }

    private fun writeU16(out: OutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeUtf8(out: OutputStream, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        writeU16(out, bytes.size)
        out.write(bytes)
    }

    private class ByteReader(private val data: ByteArray) {
        private var pos = 0

        fun remaining(): Int = data.size - pos

        fun readU8(): Int {
            if (pos >= data.size) throw EOFException("Short MQTT packet")
            return data[pos++].toInt() and 0xFF
        }

        fun readU16(): Int {
            val hi = readU8()
            val lo = readU8()
            return (hi shl 8) or lo
        }

        fun readBytesPrefixed(): ByteArray {
            val len = readU16()
            if (pos + len > data.size) throw EOFException("Short MQTT byte field")
            val slice = data.copyOfRange(pos, pos + len)
            pos += len
            return slice
        }

        fun readUtf8(): String = String(readBytesPrefixed(), StandardCharsets.UTF_8)

        fun readRemainingBytes(): ByteArray {
            val slice = data.copyOfRange(pos, data.size)
            pos = data.size
            return slice
        }
    }
}
