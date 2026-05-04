package dev.vitalcc.stukay.runtime.hostbridge

import com.sun.net.httpserver.HttpServer
import dev.vitalcc.stukay.core.model.HostBridgeTransport
import dev.vitalcc.stukay.core.model.HostId
import dev.vitalcc.stukay.core.model.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
    fun fetchRuntimeSummaryRedactsBearerTokenFromFailurePayload() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/runtime/summary") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """{"errorCode":"unauthorized","errorMessage":"Authorization: Bearer secret-token"}""".toByteArray()
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
            assertEquals("Authorization: Bearer <redacted>", error.message)
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

    @Test
    fun fetchRuntimeSummaryRedactsBearerTokenFromDegradedPayload() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/runtime/summary") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """
                {
                  "hostStatus": "degraded",
                  "runtimeReady": false,
                  "appListCount": 4,
                  "lastRoundTripMs": 25,
                  "probeAtEpochMs": 123,
                  "retryAttempt": 1,
                  "degradedReason": "Authorization: Bearer secret-token",
                  "errorCode": "unavailable",
                  "errorMessage": "Bearer secret-token",
                  "lastTransportError": "Authorization: Bearer secret-token"
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

            assertEquals("Authorization: Bearer <redacted>", payload.degradedReason)
            assertEquals("Bearer <redacted>", payload.errorMessage)
            assertEquals("Authorization: Bearer <redacted>", payload.lastTransportError)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchRuntimeSummaryDoesNotFollowRedirects() {
        val redirectedServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var redirectedEndpointHit = false
        redirectedServer.createContext("/v1/runtime/summary") { exchange ->
            redirectedEndpointHit = true
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """
                {
                  "hostStatus": "ready",
                  "runtimeReady": true,
                  "appListCount": 7,
                  "lastRoundTripMs": 15,
                  "probeAtEpochMs": 789,
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
        redirectedServer.start()

        val redirectingServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        redirectingServer.createContext("/v1/runtime/summary") { exchange ->
            exchange.responseHeaders.add(
                "Location",
                "http://127.0.0.1:${redirectedServer.address.port}/v1/runtime/summary",
            )
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        redirectingServer.start()

        try {
            val client = OkHttpHostBridgeClient()
            val error = assertThrows(HostBridgeClientException::class.java) {
                client.fetchRuntimeSummary(
                    PairingPayload(
                        version = 1,
                        hostId = HostId("host-main"),
                        hostLabel = "Office Windows",
                        endpoint = "http://127.0.0.1:${redirectingServer.address.port}",
                        transport = HostBridgeTransport.HttpJson,
                        sessionToken = "secret-token",
                    ),
                )
            }

            assertEquals(HostBridgeClientFailureCode.Protocol, error.failureCode)
            assertFalse(redirectedEndpointHit)
        } finally {
            redirectingServer.stop(0)
            redirectedServer.stop(0)
        }
    }
}
