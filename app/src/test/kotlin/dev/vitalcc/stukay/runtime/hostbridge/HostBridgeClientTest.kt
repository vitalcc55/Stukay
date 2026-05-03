package dev.vitalcc.stukay.runtime.hostbridge

import com.sun.net.httpserver.HttpServer
import dev.vitalcc.stukay.core.model.HostBridgeTransport
import dev.vitalcc.stukay.core.model.HostId
import dev.vitalcc.stukay.core.model.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class HostBridgeClientTest {
    @Test
    fun fetchRuntimeSummaryParsesReadyResponse() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/runtime/summary") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """
                {
                  "hostStatus": "ready",
                  "runtimeReady": true,
                  "appListCount": 4,
                  "lastRoundTripMs": 25,
                  "probeAtEpochMs": 123,
                  "retryAttempt": 0,
                  "degradedReason": null,
                  "errorCode": null,
                  "errorMessage": null,
                  "lastTransportError": null
                }
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            val payload = client.fetchRuntimeSummary(
                PairingPayload(
                    version = 1,
                    hostId = HostId("host-main"),
                    hostLabel = "Office Windows",
                    endpoint = "http://127.0.0.1:${server.address.port}",
                    transport = HostBridgeTransport.HttpJson,
                    sessionToken = "secret-token",
                ),
            )

            assertEquals(HostBridgeClientStatus.Ready, payload.hostStatus)
            assertEquals(true, payload.runtimeReady)
            assertEquals(4, payload.appListCount)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchRuntimeSummarySendsBearerAuthorizationHeader() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var authorizationHeader: String? = null
        server.createContext("/v1/runtime/summary") { exchange ->
            authorizationHeader = exchange.requestHeaders.getFirst("Authorization")
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """
                {
                  "hostStatus": "ready",
                  "runtimeReady": true,
                  "appListCount": 1,
                  "lastRoundTripMs": 5,
                  "probeAtEpochMs": 456,
                  "retryAttempt": 0,
                  "degradedReason": null,
                  "errorCode": null,
                  "errorMessage": null,
                  "lastTransportError": null
                }
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            client.fetchRuntimeSummary(
                PairingPayload(
                    version = 1,
                    hostId = HostId("host-main"),
                    hostLabel = "Office Windows",
                    endpoint = "http://127.0.0.1:${server.address.port}",
                    transport = HostBridgeTransport.HttpJson,
                    sessionToken = "secret-token",
                ),
            )

            assertEquals("Bearer secret-token", authorizationHeader)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchRuntimeSummaryMapsUnauthorizedResponse() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/runtime/summary") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """{"errorCode":"unauthorized","errorMessage":"Unauthorized"}""".toByteArray()
            exchange.sendResponseHeaders(401, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            val error = assertThrows(HostBridgeClientException::class.java) {
                client.fetchRuntimeSummary(
                    PairingPayload(
                        version = 1,
                        hostId = HostId("host-main"),
                        hostLabel = "Office Windows",
                        endpoint = "http://127.0.0.1:${server.address.port}",
                        transport = HostBridgeTransport.HttpJson,
                        sessionToken = "secret-token",
                    ),
                )
            }

            assertEquals(HostBridgeClientFailureCode.Unauthorized, error.failureCode)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchRuntimeSummaryMapsMalformedJsonToProtocolFailure() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/runtime/summary") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = "{\"hostStatus\":\"ready\"".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            val error = assertThrows(HostBridgeClientException::class.java) {
                client.fetchRuntimeSummary(
                    PairingPayload(
                        version = 1,
                        hostId = HostId("host-main"),
                        hostLabel = "Office Windows",
                        endpoint = "http://127.0.0.1:${server.address.port}",
                        transport = HostBridgeTransport.HttpJson,
                        sessionToken = "secret-token",
                    ),
                )
            }

            assertEquals(HostBridgeClientFailureCode.Protocol, error.failureCode)
            assertTrue(error.message.orEmpty().isNotBlank())
        } finally {
            server.stop(0)
        }
    }
}
