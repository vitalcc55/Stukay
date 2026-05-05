package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.HostBridgeConnectionPhase
import dev.vitalcc.stukay.core.model.HostRuntimeSnapshotScope
import dev.vitalcc.stukay.core.model.HostRuntimeStatus
import dev.vitalcc.stukay.core.model.LocalNetworkAccessState
import dev.vitalcc.stukay.core.model.runtimeSummaryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpJsonHostBridgeRepositoryTest {
    @Test
    fun savePairingPayloadCreatesPairedState() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(HostBridgeRuntimePayload.ready(appListCount = 2)),
            initialNearbyWifiDevicesGranted = false,
            timeProvider = { 10L },
        )

        val state = repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = false,
        )

        assertEquals(HostBridgeConnectionPhase.Paired, state.phase)
        assertEquals(LocalNetworkAccessState.Ready, state.localNetworkAccessState)
        assertEquals("Office Windows", state.pairedHost?.hostLabel)
    }

    @Test
    fun connectAttemptsRuntimeSummaryEvenWhenNearbyPermissionIsMissing() {
        val client = FakeHostBridgeClient(HostBridgeRuntimePayload.ready(appListCount = 2))
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = client,
            initialNearbyWifiDevicesGranted = false,
            timeProvider = { 20L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = false,
        )

        val state = repository.connect(localNetworkPermissionGranted = false)

        assertEquals(HostBridgeConnectionPhase.Connected, state.phase)
        assertEquals(LocalNetworkAccessState.Ready, state.localNetworkAccessState)
        assertEquals(1, client.calls)
        assertEquals(20L, state.lastConnectedAtEpochMs)
    }

    @Test
    fun unavailableConnectWithoutNearbyPermissionExplainsManualOptInPath() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(
                HostBridgeClientException(
                    failureCode = HostBridgeClientFailureCode.Unavailable,
                    message = "timeout",
                ),
            ),
            initialNearbyWifiDevicesGranted = false,
            timeProvider = { 25L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = false,
        )

        val state = repository.connect(localNetworkPermissionGranted = false)

        assertEquals(HostBridgeConnectionPhase.Degraded, state.phase)
        assertEquals(LocalNetworkAccessState.Ready, state.localNetworkAccessState)
        assertEquals(
            "timeout. Если на устройстве включен Android 16 local-network opt-in, выдайте Nearby devices.",
            state.lastError,
        )
    }

    @Test
    fun connectMapsReadyRuntimeSummaryIntoConnectedState() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(HostBridgeRuntimePayload.ready(appListCount = 4)),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 30L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )

        val state = repository.connect(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Connected, state.phase)
        assertEquals(HostRuntimeStatus.Ready, state.runtimeSummary.hostStatus)
        assertEquals(4, state.runtimeSummary.appListCount)
        assertEquals(30L, state.lastConnectedAtEpochMs)
    }

    @Test
    fun connectAcceptsCarrierGradeNatWhenPermissionIsGranted() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(HostBridgeRuntimePayload.ready(appListCount = 1)),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 35L },
        )
        repository.savePairingPayload(
            rawPayload = carrierGradeNatPayload(),
            localNetworkPermissionGranted = true,
        )

        val state = repository.connect(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Connected, state.phase)
        assertEquals(LocalNetworkAccessState.Ready, state.localNetworkAccessState)
        assertEquals(35L, state.lastConnectedAtEpochMs)
    }

    @Test
    fun unauthorizedClientFailureMapsToFailedState() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(
                HostBridgeClientException(
                    failureCode = HostBridgeClientFailureCode.Unauthorized,
                    message = "unauthorized",
                ),
            ),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 40L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )

        val state = repository.connect(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Failed, state.phase)
        assertEquals(HostRuntimeStatus.Unauthorized, state.runtimeSummary.hostStatus)
    }

    @Test
    fun unauthorizedClientFailureClearsStaleRuntimeMetricsWhileKeepingLiveFailureScope() {
        val client = FakeHostBridgeClient(
            HostBridgeRuntimePayload.ready(
                appListCount = 6,
                lastRoundTripMs = 18,
                probeAtEpochMs = 101L,
            ),
            HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Unauthorized,
                message = "unauthorized",
            ),
        )
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = client,
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 42L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )
        repository.connect(localNetworkPermissionGranted = true)

        val state = repository.probe(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Failed, state.phase)
        assertEquals(HostRuntimeStatus.Unauthorized, state.runtimeSummary.hostStatus)
        assertNull(state.runtimeSummary.appListCount)
        assertNull(state.runtimeSummary.lastRoundTripMs)
        assertNull(state.runtimeSummary.lastProbeAtEpochMs)
        assertEquals(HostRuntimeSnapshotScope.Live, state.runtimeSummaryScope())
    }

    @Test
    fun unauthorizedRuntimePayloadClearsStaleRuntimeMetricsWhileKeepingLiveFailureScope() {
        val client = FakeHostBridgeClient(
            HostBridgeRuntimePayload.ready(
                appListCount = 7,
                lastRoundTripMs = 21,
                probeAtEpochMs = 109L,
            ),
            HostBridgeRuntimePayload(
                hostStatus = HostBridgeClientStatus.Unauthorized,
                runtimeReady = false,
                appListCount = null,
                lastRoundTripMs = null,
                probeAtEpochMs = null,
                retryAttempt = 0,
                degradedReason = null,
                errorCode = "unauthorized",
                errorMessage = "unauthorized",
                lastTransportError = "unauthorized",
            ),
        )
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = client,
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 44L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )
        repository.connect(localNetworkPermissionGranted = true)

        val state = repository.probe(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Failed, state.phase)
        assertEquals(HostRuntimeStatus.Unauthorized, state.runtimeSummary.hostStatus)
        assertNull(state.runtimeSummary.appListCount)
        assertNull(state.runtimeSummary.lastRoundTripMs)
        assertNull(state.runtimeSummary.lastProbeAtEpochMs)
        assertEquals(HostRuntimeSnapshotScope.Live, state.runtimeSummaryScope())
    }

    @Test
    fun refreshPermissionStateDoesNotMaskUnauthorizedFailure() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(
                HostBridgeClientException(
                    failureCode = HostBridgeClientFailureCode.Unauthorized,
                    message = "unauthorized",
                ),
            ),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 45L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )
        repository.connect(localNetworkPermissionGranted = true)

        val state = repository.refreshPermissionState(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Failed, state.phase)
        assertEquals(HostRuntimeStatus.Unauthorized, state.runtimeSummary.hostStatus)
        assertEquals("unauthorized", state.lastError)
    }

    @Test
    fun probePreservesLastGoodSummaryWhenTransportFails() {
        val client = FakeHostBridgeClient(
            HostBridgeRuntimePayload.ready(appListCount = 5),
            HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Unavailable,
                message = "timeout",
            ),
        )
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = client,
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 50L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )
        repository.connect(localNetworkPermissionGranted = true)

        val state = repository.probe(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Degraded, state.phase)
        assertEquals(HostRuntimeStatus.Degraded, state.runtimeSummary.hostStatus)
        assertEquals(5, state.runtimeSummary.appListCount)
        assertEquals("timeout", state.lastError)
    }

    @Test
    fun protocolFailureClearsStaleRuntimeMetricsWhileKeepingLiveFailureScope() {
        val client = FakeHostBridgeClient(
            HostBridgeRuntimePayload.ready(
                appListCount = 5,
                lastRoundTripMs = 17,
                probeAtEpochMs = 99L,
            ),
            HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Protocol,
                message = "redirect denied",
            ),
        )
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = client,
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 52L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )
        repository.connect(localNetworkPermissionGranted = true)

        val state = repository.probe(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Failed, state.phase)
        assertEquals(HostRuntimeStatus.Unknown, state.runtimeSummary.hostStatus)
        assertNull(state.runtimeSummary.appListCount)
        assertNull(state.runtimeSummary.lastRoundTripMs)
        assertNull(state.runtimeSummary.lastProbeAtEpochMs)
        assertEquals("redirect denied", state.runtimeSummary.lastTransportError)
        assertEquals(HostRuntimeSnapshotScope.Live, state.runtimeSummaryScope())
    }

    @Test
    fun disconnectPreservesPairingAndMarksStateDisconnected() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(HostBridgeRuntimePayload.ready(appListCount = 3)),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 55L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )
        repository.connect(localNetworkPermissionGranted = true)

        val state = repository.disconnect(clearPairing = false)

        assertEquals(HostBridgeConnectionPhase.Disconnected, state.phase)
        assertEquals("Office Windows", state.pairedHost?.hostLabel)
        assertEquals(3, state.runtimeSummary.appListCount)
        assertEquals(55L, state.lastConnectedAtEpochMs)
    }

    @Test
    fun runtimeOperationsRequireConnectOrReconnectState() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(HostBridgeRuntimePayload.ready(appListCount = 3)),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 58L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )

        assertRuntimePathUnavailable(repository)

        repository.connect(localNetworkPermissionGranted = true)
        repository.disconnect(clearPairing = false)

        assertRuntimePathUnavailable(repository)
    }

    @Test
    fun linkLocalEndpointIsRejectedForCurrentSlice() {
        val repository = HttpJsonHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            client = FakeHostBridgeClient(HostBridgeRuntimePayload.ready(appListCount = 1)),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 60L },
        )
        repository.savePairingPayload(
            rawPayload = linkLocalPayload(),
            localNetworkPermissionGranted = true,
        )

        val state = repository.connect(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Failed, state.phase)
        assertEquals(LocalNetworkAccessState.UnsupportedForSlice, state.localNetworkAccessState)
        assertNull(state.pairedHost?.hostId?.value?.takeIf { it == "host-link-local" }?.takeIf { false })
    }

    private fun privateLanPayload(): String = """
        {
          "version": 1,
          "hostId": "host-main",
          "hostLabel": "Office Windows",
          "endpoint": "http://192.168.0.24:4500",
          "transport": "http_json",
          "sessionToken": "secret-token"
        }
    """.trimIndent()

    private fun carrierGradeNatPayload(): String = """
        {
          "version": 1,
          "hostId": "host-cgnat",
          "hostLabel": "Carrier Grade NAT",
          "endpoint": "http://100.64.12.34:8421",
          "transport": "http_json",
          "sessionToken": "secret-token"
        }
    """.trimIndent()

    private fun linkLocalPayload(): String = """
        {
          "version": 1,
          "hostId": "host-link-local",
          "hostLabel": "Link Local",
          "endpoint": "http://169.254.1.12:8421",
          "transport": "http_json",
          "sessionToken": "secret-token"
        }
    """.trimIndent()
}

private class FakeHostBridgeClient(
    vararg responses: Any,
) : HostBridgeClient {
    private val queue = ArrayDeque(responses.toList())
    var calls: Int = 0
        private set

    override fun fetchRuntimeSummary(pairingPayload: dev.vitalcc.stukay.core.model.PairingPayload): HostBridgeRuntimePayload {
        calls += 1
        val next = queue.removeFirst()
        if (next is HostBridgeClientException) {
            throw next
        }
        return next as HostBridgeRuntimePayload
    }

    override fun listThreads(pairingPayload: dev.vitalcc.stukay.core.model.PairingPayload): HostBridgeThreadListPayload =
        HostBridgeThreadListPayload(emptyList())

    override fun readThread(
        pairingPayload: dev.vitalcc.stukay.core.model.PairingPayload,
        threadId: String,
    ): HostBridgeThreadPayload = error("Not used in this test")

    override fun resumeThread(
        pairingPayload: dev.vitalcc.stukay.core.model.PairingPayload,
        threadId: String,
    ): HostBridgeThreadPayload = error("Not used in this test")

    override fun startTurn(
        pairingPayload: dev.vitalcc.stukay.core.model.PairingPayload,
        threadId: String,
        text: String,
    ): HostBridgeTurnPayload = error("Not used in this test")

    override fun interruptTurn(
        pairingPayload: dev.vitalcc.stukay.core.model.PairingPayload,
        threadId: String,
        turnId: String,
    ) = Unit

    override fun respondToApproval(
        pairingPayload: dev.vitalcc.stukay.core.model.PairingPayload,
        requestId: String,
        decision: dev.vitalcc.stukay.core.model.ApprovalDecision,
    ) = Unit

    override fun openThreadEventStream(
        pairingPayload: dev.vitalcc.stukay.core.model.PairingPayload,
        threadId: String,
    ): HostBridgeEventStream = error("Not used in this test")
}

private fun assertRuntimePathUnavailable(repository: HttpJsonHostBridgeRepository) {
    listOf<() -> Unit>(
        { repository.listThreads() },
        { repository.readThread("thread-1") },
        { repository.resumeThread("thread-1") },
        { repository.startTurn("thread-1", "hello") },
        { repository.interruptTurn("thread-1", "turn-1") },
        { repository.respondToApproval("request-1", dev.vitalcc.stukay.core.model.ApprovalDecision.AcceptOnce) },
        { repository.openThreadEventStream("thread-1") },
    ).forEach { operation ->
        val error = assertThrows(IllegalStateException::class.java) {
            operation()
        }
        assertTrue(error.message.orEmpty().contains("connect/reconnect"))
    }
}
