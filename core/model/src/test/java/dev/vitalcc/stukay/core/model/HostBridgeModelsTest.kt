package dev.vitalcc.stukay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBridgeModelsTest {
    @Test
    fun pairingPayloadToStringRedactsSessionToken() {
        val payload = PairingPayload(
            version = 1,
            hostId = HostId("host-main"),
            hostLabel = "Office Windows",
            endpoint = "http://192.168.0.24:4500",
            transport = HostBridgeTransport.HttpJson,
            sessionToken = "secret-token",
        )

        val rendered = payload.toString()

        assertTrue(rendered.contains("sessionToken=<redacted>"))
        assertFalse(rendered.contains("secret-token"))
    }

    @Test
    fun connectionStateCanCarryDegradedRuntimeSummary() {
        val summary = HostRuntimeSummary(
            hostStatus = HostRuntimeStatus.Degraded,
            runtimeReady = false,
            appListCount = 3,
            lastRoundTripMs = 480,
            retryAttempt = 2,
            degradedReason = "timeout",
            lastTransportError = "Socket timeout",
        )

        val state = HostBridgeConnectionState(
            phase = HostBridgeConnectionPhase.Degraded,
            runtimeSummary = summary,
        )

        assertTrue(state.phase == HostBridgeConnectionPhase.Degraded)
        assertTrue(state.runtimeSummary.hostStatus == HostRuntimeStatus.Degraded)
        assertTrue(state.runtimeSummary.appListCount == 3)
        assertTrue(state.runtimeSummary.lastTransportError == "Socket timeout")
    }

    @Test
    fun runtimeSummaryScopeIsLiveWhenConnectionIsConnected() {
        val state = HostBridgeConnectionState(
            phase = HostBridgeConnectionPhase.Connected,
            runtimeSummary = HostRuntimeSummary(
                hostStatus = HostRuntimeStatus.Ready,
                runtimeReady = true,
                appListCount = 4,
            ),
        )

        assertEquals(HostRuntimeSnapshotScope.Live, state.runtimeSummaryScope())
    }

    @Test
    fun runtimeSummaryScopeIsLastKnownWhenDisconnectedKeepsSnapshot() {
        val state = HostBridgeConnectionState(
            phase = HostBridgeConnectionPhase.Disconnected,
            runtimeSummary = HostRuntimeSummary(
                hostStatus = HostRuntimeStatus.Ready,
                runtimeReady = true,
                appListCount = 4,
            ),
        )

        assertEquals(HostRuntimeSnapshotScope.LastKnown, state.runtimeSummaryScope())
    }

    @Test
    fun runtimeSummaryScopeIsNoneWhenSnapshotIsEmpty() {
        val state = HostBridgeConnectionState(
            phase = HostBridgeConnectionPhase.NotPaired,
            runtimeSummary = HostRuntimeSummary(),
        )

        assertEquals(HostRuntimeSnapshotScope.None, state.runtimeSummaryScope())
    }

    @Test
    fun runtimeSummaryScopeIsLiveForFreshUnauthorizedFailure() {
        val state = HostBridgeConnectionState(
            phase = HostBridgeConnectionPhase.Failed,
            runtimeSummary = HostRuntimeSummary(
                hostStatus = HostRuntimeStatus.Unauthorized,
                runtimeReady = false,
                lastTransportError = "unauthorized",
            ),
        )

        assertEquals(HostRuntimeSnapshotScope.Live, state.runtimeSummaryScope())
    }
}
