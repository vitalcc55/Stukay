package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.HostBridgeTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBridgePairingParserTest {
    @Test
    fun parsesValidHttpJsonPayload() {
        val payload = parsePairingPayload(
            validPayload(endpoint = "http://192.168.0.24:4500"),
        )

        assertEquals(1, payload.version)
        assertEquals("host-main", payload.hostId.value)
        assertEquals("Office Windows", payload.hostLabel)
        assertEquals("http://192.168.0.24:4500", payload.endpoint)
        assertEquals(HostBridgeTransport.HttpJson, payload.transport)
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

        assertTrue(error.message.orEmpty().contains("http/https"))
    }

    @Test
    fun rejectsWebSocketTransportForMvp() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parsePairingPayload(
                validPayload(
                    endpoint = "ws://192.168.0.24:4500",
                    transport = "ws",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("http_json"))
    }

    @Test
    fun rejectsWebSocketEndpointSchemeForMvp() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parsePairingPayload(
                validPayload(
                    endpoint = "wss://192.168.0.24:4500",
                    transport = "http_json",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("http_json"))
    }

    @Test
    fun rejectsEndpointThatAlreadyContainsSummaryPath() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parsePairingPayload(
                validPayload(
                    endpoint = "http://192.168.0.24:4500/v1/runtime/summary",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("base host endpoint"))
    }

    private fun validPayload(
        endpoint: String,
        transport: String = "http_json",
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
