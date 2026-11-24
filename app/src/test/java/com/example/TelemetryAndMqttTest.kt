package com.example

import com.example.model.DownlinkFormat
import com.example.mqtt.EmbeddedMqttBroker
import com.example.mqtt.ExternalMqttClient
import com.example.mqtt.MdnsCodec
import com.example.mqtt.MqttCodec
import com.example.mqtt.MqttPacket
import com.example.telemetry.DownlinkCommandBuilder
import com.example.telemetry.IngestResult
import com.example.telemetry.TelemetryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TelemetryAndMqttTest {

    @Test
    fun `full report merges with periodic update and preserves missing fields as null`() {
        val engine = TelemetryEngine()

        // 1. Deliver full report to uppercase MAC topic -> should normalize to lowercase and NOT add history
        val reportRes = engine.ingest(
            topic = "/dpub/0011223344AA",
            payload = """{"method":"report","ver":"v1.1.41","device_type":"CUNZHI01","sleep_time":7200,"battery":85,"report_delay_ms":5000,"pressure":10.5}""",
            brokerReceivedAtMs = 1_000L,
            appliedAtMs = 1_005L
        )
        assertTrue(reportRes is IngestResult.Accepted)
        assertFalse((reportRes as IngestResult.Accepted).addedHistorySample)
        assertEquals("0011223344aa", reportRes.mac)

        var snap = engine.computeSnapshots(nowMs = 2_000L).single()
        assertEquals("v1.1.41", snap.firmwareVersion)
        assertEquals("CUNZHI01", snap.deviceType)
        assertEquals(85, snap.battery)
        assertEquals(10.5, snap.pressure1!!, 0.001)
        assertNull(snap.pressure2) // missing pressure1 must stay null, never 0
        assertEquals(0, snap.history.size)

        // 2. Deliver periodic update with both pressure channels (including valid 0.0)
        val update1 = engine.ingest(
            topic = "/dpub/0011223344aa",
            payload = """{"method":"update","pressure":85.7,"pressure1":0,"battery":84,"game_cz_count":3}""",
            brokerReceivedAtMs = 6_000L,
            appliedAtMs = 6_002L
        )
        assertTrue((update1 as IngestResult.Accepted).addedHistorySample)

        // 3. Deliver periodic update missing pressure1 -> sample.second must be null while field retains last value
        engine.ingest(
            topic = "/dpub/0011223344aa",
            payload = """{"method":"update","pressure":91.2}""",
            brokerReceivedAtMs = 11_000L,
            appliedAtMs = 11_004L
        )

        snap = engine.computeSnapshots(nowMs = 12_000L).single()
        assertEquals("v1.1.41", snap.firmwareVersion) // version preserved
        assertEquals(91.2, snap.pressure1!!, 0.001)
        assertEquals(0.0, snap.pressure2!!, 0.001) // explicit 0 preserved as valid reading
        assertEquals(3, snap.czCount)
        assertEquals(2, snap.history.size)
        assertEquals(85.7, snap.history[0].first!!, 0.001)
        assertEquals(0.0, snap.history[0].second!!, 0.001)
        assertEquals(91.2, snap.history[1].first!!, 0.001)
        assertNull(snap.history[1].second)
    }

    @Test
    fun `single item query reply updates only targeted field and ignores invalid inputs`() {
        val engine = TelemetryEngine()
        engine.ingest(
            topic = "/dpub/001122334455",
            payload = """{"method":"report","ver":"v1.1.41","report_delay_ms":5000,"battery":90}""",
            appliedAtMs = 1_000L
        )

        val single = engine.ingest(
            topic = "/dpub/001122334455",
            payload = """{"method":"update","key":"report_delay_ms","value":8000,"msg_id":1}""",
            appliedAtMs = 2_000L
        )
        assertTrue(single is IngestResult.Accepted)
        assertFalse((single as IngestResult.Accepted).addedHistorySample)

        val snap = engine.computeSnapshots(nowMs = 3_000L).single()
        assertEquals(8000, snap.reportDelayMs)
        assertEquals(90, snap.battery)
        assertEquals(24_000L, snap.timeoutMs) // max(15s, 8s * 3) = 24s

        // Invalid topic, JSON, and unknown method must be ignored
        assertTrue(engine.ingest("/drecv/001122334455", """{"method":"update","pressure":5}""") is IngestResult.Ignored)
        assertTrue(engine.ingest("/dpub/shortmac", """{"method":"update","pressure":5}""") is IngestResult.Ignored)
        assertTrue(engine.ingest("/dpub/001122334455", "{bad json") is IngestResult.Ignored)
        assertTrue(engine.ingest("/dpub/001122334455", """{"method":"set","power":1}""") is IngestResult.Ignored)
    }

    @Test
    fun `timeout and 180-point history cap follow specification`() {
        val engine = TelemetryEngine()
        engine.ingest(
            topic = "/dpub/90649b4ed93c",
            payload = """{"method":"report","report_delay_ms":2000}""",
            appliedAtMs = 10_000L
        )

        // report_delay_ms=2000 -> 3x = 6000ms, so min timeout 15_000ms applies
        assertTrue(engine.computeSnapshots(nowMs = 25_000L).single().online)
        assertFalse(engine.computeSnapshots(nowMs = 25_001L).single().online)

        // Feed 200 periodic updates -> only latest 180 retained
        for (i in 1..200) {
            engine.ingest(
                topic = "/dpub/90649b4ed93c",
                payload = """{"method":"update","pressure":$i,"pressure1":${i * 2}}""",
                appliedAtMs = 30_000L + i * 100L
            )
        }
        val snap = engine.computeSnapshots(nowMs = 55_000L).single()
        assertEquals(180, snap.history.size)
        assertEquals(21.0, snap.history.first().first!!, 0.001)
        assertEquals(200.0, snap.history.last().first!!, 0.001)
    }

    @Test
    fun `mqtt packet codec roundtrip and topic matching work accurately`() {
        val publish = MqttPacket.Publish(
            topic = "/dpub/90649b4ed93c",
            payload = """{"method":"update","pressure":42.5}""".toByteArray(),
            qos = 1,
            retain = false,
            packetId = 77
        )
        val encoded = MqttCodec.encodePacket(publish)
        val decoded = MqttCodec.readPacket(ByteArrayInputStream(encoded)) as MqttPacket.Publish
        assertEquals("/dpub/90649b4ed93c", decoded.topic)
        assertEquals(1, decoded.qos)
        assertEquals(77, decoded.packetId)
        assertEquals("""{"method":"update","pressure":42.5}""", decoded.payloadUtf8())

        assertTrue(MqttCodec.topicMatchesFilter("/dpub/90649b4ed93c", "/dpub/+"))
        assertFalse(MqttCodec.topicMatchesFilter("/drecv/90649b4ed93c", "/dpub/+"))
        assertTrue(MqttCodec.topicMatchesFilter("/dpub/90649b4ed93c", "#"))
    }

    @Test
    fun `mdns codec detects easysmart local query and builds valid ipv4 A response`() {
        val query = ByteArrayOutputStream().apply {
            // Header: ID=0, Flags=0, QDCOUNT=1, AN/NS/AR=0
            write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            // QNAME: easysmart.local
            write(9)
            write("easysmart".toByteArray())
            write(5)
            write("local".toByteArray())
            write(0)
            // QTYPE=A (1), QCLASS=IN (1)
            write(byteArrayOf(0, 1, 0, 1))
        }.toByteArray()

        assertTrue(MdnsCodec.isQueryForHost(query, query.size, "easysmart.local"))
        assertFalse(MdnsCodec.isQueryForHost(query, query.size, "other.local"))

        val response = MdnsCodec.buildARecordResponse("easysmart.local", "192.168.10.56")
        assertNotNull(response)
        // Last 4 bytes must equal 192.168.10.56
        val r = response!!
        assertEquals(192, r[r.size - 4].toInt() and 0xFF)
        assertEquals(168, r[r.size - 3].toInt() and 0xFF)
        assertEquals(10, r[r.size - 2].toInt() and 0xFF)
        assertEquals(56, r[r.size - 1].toInt() and 0xFF)
    }

    @Test
    fun `embedded broker receives qos1 publish over tcp and sends puback`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val receivedLatch = CountDownLatch(1)
        var capturedTopic = ""
        var capturedPayload = ""

        val broker = EmbeddedMqttBroker(
            scope = scope,
            onTelemetryPublish = { topic, payload, _ ->
                capturedTopic = topic
                capturedPayload = payload
                receivedLatch.countDown()
            },
            onClientCountChanged = {},
            onLog = { _, _, _ -> }
        )

        val port = 28883
        try {
            broker.start(port = port, bindAddress = "127.0.0.1")

            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 3_000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                MqttCodec.writePacket(
                    out,
                    MqttPacket.Connect("MQTT", 4, true, 30, "cunzhi-test-device")
                )
                val connAck = MqttCodec.readPacket(input) as MqttPacket.ConnAck
                assertEquals(0, connAck.returnCode)

                MqttCodec.writePacket(
                    out,
                    MqttPacket.Publish(
                        topic = "/dpub/001122334455",
                        payload = """{"method":"update","pressure":55.5,"pressure1":12.0,"battery":92,"game_cz_count":1}""".toByteArray(),
                        qos = 1,
                        packetId = 9
                    )
                )
                val pubAck = MqttCodec.readPacket(input) as MqttPacket.PubAck
                assertEquals(9, pubAck.packetId)

                MqttCodec.writePacket(out, MqttPacket.Disconnect)
            }

            assertTrue(receivedLatch.await(3, TimeUnit.SECONDS))
            assertEquals("/dpub/001122334455", capturedTopic)
            assertTrue(capturedPayload.contains("55.5"))
        } finally {
            broker.stop()
            scope.cancel()
        }
    }

    @Test
    fun `external endpoint parser supports mqtt scheme bare host and custom port`() {
        val p1 = ExternalMqttClient.parseEndpoint("mqtt://192.168.10.56:1883")
        assertEquals("192.168.10.56", p1?.host)
        assertEquals(1883, p1?.port)

        val p2 = ExternalMqttClient.parseEndpoint("easysmart.local")
        assertEquals("easysmart.local", p2?.host)
        assertEquals(1883, p2?.port)

        val p3 = ExternalMqttClient.parseEndpoint("10.0.0.2:2883")
        assertEquals("10.0.0.2", p3?.host)
        assertEquals(2883, p3?.port)
    }

    @Test
    fun `downlink command builder formats get and set commands cleanly`() {
        assertEquals("/drecv/90649b4ed93c", DownlinkCommandBuilder.downlinkTopic("90649B4ED93C"))
        assertEquals(
            """{"method":"get","key":"report_delay_ms","msg_id":1}""",
            DownlinkCommandBuilder.buildGetCommand("report_delay_ms", 1)
        )
        assertEquals(
            """{"method":"set","key":"power","value":50,"msg_id":2}""",
            DownlinkCommandBuilder.buildSetCommand("power", 50, 2, DownlinkFormat.KEY_VALUE)
        )
        assertEquals(
            """{"method":"set","power":50,"msg_id":3}""",
            DownlinkCommandBuilder.buildSetCommand("power", 50, 3, DownlinkFormat.FLAT)
        )
        assertEquals(
            """{"method":"set","key":"game_p1_thresh","value":125.5,"msg_id":4}""",
            DownlinkCommandBuilder.buildSetCommand("game_p1_thresh", 125.5, 4, DownlinkFormat.KEY_VALUE)
        )
    }

    @Test
    fun `embedded broker delivers qos1 downlink command to subscribed device`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val broker = EmbeddedMqttBroker(
            scope = scope,
            onTelemetryPublish = { _, _, _ -> },
            onClientCountChanged = {},
            onLog = { _, _, _ -> }
        )

        val port = 28884
        try {
            broker.start(port = port, bindAddress = "127.0.0.1")

            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 3_000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                MqttCodec.writePacket(
                    out,
                    MqttPacket.Connect("MQTT", 4, true, 30, "cunzhi-downlink-test")
                )
                val connAck = MqttCodec.readPacket(input) as MqttPacket.ConnAck
                assertEquals(0, connAck.returnCode)

                // Device subscribes to its downlink topic /drecv/001122334455
                MqttCodec.writePacket(
                    out,
                    MqttPacket.Subscribe(packetId = 5, subscriptions = listOf("/drecv/001122334455" to 1))
                )
                val subAck = MqttCodec.readPacket(input) as MqttPacket.SubAck
                assertEquals(5, subAck.packetId)

                val payload = DownlinkCommandBuilder.buildSetCommand("power", 60, 12, DownlinkFormat.KEY_VALUE)
                val delivered = broker.publishDownlink("/drecv/001122334455", payload, qos = 1)
                assertEquals(1, delivered)

                val received = MqttCodec.readPacket(input) as MqttPacket.Publish
                assertEquals("/drecv/001122334455", received.topic)
                assertEquals(1, received.qos)
                assertEquals(payload, received.payloadUtf8())

                MqttCodec.writePacket(out, MqttPacket.Disconnect)
            }
        } finally {
            broker.stop()
            scope.cancel()
        }
    }

    @Test
    fun `safe mode determines voltage ceiling 36V vs 75V and stim bounds match specification`() {
        val engine = TelemetryEngine()

        // Default / unknown safe -> fail-safe 36V ceiling
        engine.ingest("/dpub/aabbccddeeff", """{"method":"report","battery":90}""", appliedAtMs = 1_000L)
        assertEquals(36, engine.computeSnapshots(nowMs = 2_000L).single().maxVoltageLimitV)

        // safe=1 -> 36V ceiling
        engine.ingest("/dpub/aabbccddeeff", """{"method":"update","key":"safe","value":1}""", appliedAtMs = 2_500L)
        val safeSnap = engine.computeSnapshots(nowMs = 3_000L).single()
        assertEquals(1, safeSnap.safeMode)
        assertEquals(36, safeSnap.maxVoltageLimitV)

        // safe=0 -> 75V ceiling
        engine.ingest("/dpub/aabbccddeeff", """{"method":"update","key":"safe","value":0}""", appliedAtMs = 3_500L)
        val highSnap = engine.computeSnapshots(nowMs = 4_000L).single()
        assertEquals(0, highSnap.safeMode)
        assertEquals(75, highSnap.maxVoltageLimitV)

        // Delay and voltage schema constants
        assertEquals(36, com.example.model.CunzhiProtocolSchema.SAFE_VOLTAGE_LIMIT_V)
        assertEquals(75, com.example.model.CunzhiProtocolSchema.UNSAFE_VOLTAGE_LIMIT_V)
        assertEquals(20, com.example.model.CunzhiProtocolSchema.MIN_STIM_DELAY_MS)
        assertEquals(1000, com.example.model.CunzhiProtocolSchema.MAX_STIM_DELAY_MS)
    }
}

