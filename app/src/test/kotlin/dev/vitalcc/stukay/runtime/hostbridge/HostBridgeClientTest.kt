package dev.vitalcc.stukay.runtime.hostbridge

import com.sun.net.httpserver.HttpServer
import dev.vitalcc.stukay.core.model.ApprovalDecision
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

    @Test
    fun listThreadsParsesRuntimeBackedThreadPayload() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/threads/list") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """
                {
                  "data": [
                    {
                      "id": "thread-1",
                      "cwd": "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                      "title": "Runtime thread",
                      "preview": "First preview",
                      "sourceKind": "appServer",
                      "updatedAtEpochMs": 2000,
                      "createdAtEpochMs": 1000,
                      "turnCount": 1,
                      "status": {
                        "type": "active",
                        "activeFlags": ["waitingOnApproval"]
                      }
                    }
                  ]
                }
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            val payload = client.listThreads(pairingPayload(server.address.port))

            assertEquals(1, payload.data.size)
            assertEquals("thread-1", payload.data.single().id)
            assertEquals("active", payload.data.single().status.type)
            assertEquals(listOf("waitingOnApproval"), payload.data.single().status.activeFlags)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun readThreadParsesTimelineItems() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/threads/thread-1") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """
                {
                  "thread": {
                    "id": "thread-1",
                    "cwd": "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                    "title": "Runtime thread",
                    "preview": "First preview",
                    "sourceKind": "appServer",
                    "updatedAtEpochMs": 2000,
                    "createdAtEpochMs": 1000,
                    "turnCount": 1,
                    "status": {"type": "idle", "activeFlags": []},
                    "timeline": [
                      {"type": "userMessage", "id": "user-1", "threadId": "thread-1", "turnId": "turn-1", "text": "Hello"},
                      {"type": "assistantMessage", "id": "assistant-1", "threadId": "thread-1", "turnId": "turn-1", "itemId": "assistant-1", "text": "World", "streaming": false}
                    ]
                  }
                }
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            val payload = client.readThread(pairingPayload(server.address.port), "thread-1")

            assertEquals(2, payload.timeline.size)
            assertEquals("assistantMessage", payload.timeline[1].type)
            assertEquals("assistant-1", payload.timeline[1].itemId)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun respondToApprovalMapsAllSupportedScalarDecisions() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val decisions = mutableListOf<String>()
        server.createContext("/v1/approvals/request-1/respond") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            val bodyText = exchange.requestBody.reader().readText()
            decisions += Regex("\"decision\"\\s*:\\s*\"([^\"]+)\"").find(bodyText)?.groupValues?.get(1).orEmpty()
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            client.respondToApproval(pairingPayload(server.address.port), "request-1", ApprovalDecision.AcceptSession)
            client.respondToApproval(pairingPayload(server.address.port), "request-1", ApprovalDecision.Decline)
            client.respondToApproval(pairingPayload(server.address.port), "request-1", ApprovalDecision.Cancel)

            assertEquals(listOf("acceptForSession", "decline", "cancel"), decisions)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun openThreadEventStreamParsesApprovalRequestEvent() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/threads/thread-1/events") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            val body = """
                data: {"method":"item/commandExecution/requestApproval","threadId":"thread-1","requestId":"request-1","approval":{"id":"approval-1","requestId":"request-1","itemId":"item-1","threadId":"thread-1","turnId":"turn-1","kind":"command","title":"Approve command","description":"Need to run command","availableDecisions":["accept","acceptForSession","decline","cancel"],"command":"dir","cwd":"C:\\Users\\v.vlasov\\Desktop\\Stukay"}}

            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            client.openThreadEventStream(pairingPayload(server.address.port), "thread-1").use { stream ->
                val event = stream.nextEvent()
                assertEquals("item/commandExecution/requestApproval", event?.method)
                assertEquals("request-1", event?.requestId)
                assertEquals("approval-1", event?.approval?.id)
                assertEquals(listOf("accept", "acceptForSession", "decline", "cancel"), event?.approval?.availableDecisions)
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun openThreadEventStreamParsesFileChangeApprovalAndEofTerminatedEvent() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/threads/thread-1/events") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            val body = "data: {\"method\":\"item/fileChange/requestApproval\",\"threadId\":\"thread-1\",\"requestId\":\"request-2\",\"approval\":{\"id\":\"approval-2\",\"requestId\":\"request-2\",\"itemId\":\"item-2\",\"threadId\":\"thread-1\",\"turnId\":\"turn-2\",\"kind\":\"fileChange\",\"title\":\"Approve file change\",\"description\":\"Need to write file\",\"availableDecisions\":[\"accept\",\"decline\",\"cancel\"],\"grantRoot\":\"C:\\\\Users\\\\v.vlasov\\\\Desktop\\\\Stukay\"}}".toByteArray()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            client.openThreadEventStream(pairingPayload(server.address.port), "thread-1").use { stream ->
                val event = stream.nextEvent()
                assertEquals("item/fileChange/requestApproval", event?.method)
                assertEquals("fileChange", event?.approval?.kind)
                assertEquals("C:\\Users\\v.vlasov\\Desktop\\Stukay", event?.approval?.grantRoot)
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun openThreadEventStreamParsesNetworkApprovalContext() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/threads/thread-1/events") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            val body = """
                data: {"method":"item/commandExecution/requestApproval","threadId":"thread-1","requestId":"request-3","approval":{"id":"approval-3","requestId":"request-3","itemId":"item-3","threadId":"thread-1","turnId":"turn-3","kind":"command","title":"Approve network access","description":"Need https access","availableDecisions":["accept","acceptForSession","decline","cancel"],"networkHost":"api.openai.com","networkProtocol":"https"}}

            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = OkHttpHostBridgeClient()
            client.openThreadEventStream(pairingPayload(server.address.port), "thread-1").use { stream ->
                val event = stream.nextEvent()
                assertEquals("api.openai.com", event?.approval?.networkHost)
                assertEquals("https", event?.approval?.networkProtocol)
            }
        } finally {
            server.stop(0)
        }
    }

    private fun pairingPayload(port: Int): PairingPayload = PairingPayload(
        version = 1,
        hostId = HostId("host-main"),
        hostLabel = "Office Windows",
        endpoint = "http://127.0.0.1:$port",
        transport = HostBridgeTransport.HttpJson,
        sessionToken = "secret-token",
    )
}
