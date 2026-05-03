package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.HostBridgeTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBridgePairingParserTest {
    @Test
    fun parsesValidWebSocketPayload() {
        val payload = parsePairingPayload(
            validPayload(endpoint = "ws://192.168.0.24:4500"),
        )

        assertEquals(1, payload.version)
        assertEquals("host-main", payload.hostId.value)
        assertEquals("Office Windows", payload.hostLabel)
        assertEquals("ws://192.168.0.24:4500", payload.endpoint)
        assertEquals(HostBridgeTransport.WebSocketJsonRpc, payload.transport)
    }

    @Test
    fun rejectsNonJsonPayload() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parsePairingPayload("not-a-json-payload")
        }

        assertTrue(error.message.orEmpty().contains("JSON pairing payload"))
    }

    @Test
    fun rejectsUnsupportedEndpointScheme() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parsePairingPayload(validPayload(endpoint = "ftp://192.168.0.24:4500"))
        }

        assertTrue(error.message.orEmpty().contains("http/https/ws/wss"))
    }

    @Test
    fun rejectsTransportEndpointMismatch() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parsePairingPayload(
                validPayload(
                    endpoint = "http://192.168.0.24:4500",
                    transport = "ws",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("scheme"))
    }

    private fun validPayload(
        endpoint: String,
        transport: String = "ws",
    ): String = """
        {
          "version": 1,
          "hostId": "host-main",
          "hostLabel": "Office Windows",
          "endpoint": "$endpoint",
          "transport": "$transport",
          "sessionToken": "secret-token"
        }
    """.trimIndent()
}
