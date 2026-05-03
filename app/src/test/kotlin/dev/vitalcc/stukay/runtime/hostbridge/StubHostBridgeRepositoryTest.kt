package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.HostBridgeConnectionPhase
import dev.vitalcc.stukay.core.model.LocalNetworkAccessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StubHostBridgeRepositoryTest {
    @Test
    fun savePairingPayloadCreatesPairedState() {
        val repository = StubHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
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
    fun connectAllowsPrivateLanWithoutNearbyDevices() {
        val repository = StubHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
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
        assertNull(state.lastError)
    }

    @Test
    fun connectSucceedsAfterPermissionIsGranted() {
        val repository = StubHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 30L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )

        val state = repository.connect(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Connected, state.phase)
        assertEquals(LocalNetworkAccessState.Ready, state.localNetworkAccessState)
        assertEquals(30L, state.lastConnectedAtEpochMs)
    }

    @Test
    fun publicEndpointIsRejectedForCurrentSlice() {
        val repository = StubHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 40L },
        )
        repository.savePairingPayload(
            rawPayload = publicEndpointPayload(),
            localNetworkPermissionGranted = true,
        )

        val state = repository.connect(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Failed, state.phase)
        assertEquals(LocalNetworkAccessState.UnsupportedForSlice, state.localNetworkAccessState)
        assertTrue(state.lastError.orEmpty().contains("private LAN"))
    }

    @Test
    fun disconnectCanForgetSavedPairing() {
        val repository = StubHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 50L },
        )
        repository.savePairingPayload(
            rawPayload = privateLanPayload(),
            localNetworkPermissionGranted = true,
        )

        val state = repository.disconnect(clearPairing = true)

        assertEquals(HostBridgeConnectionPhase.NotPaired, state.phase)
        assertEquals(LocalNetworkAccessState.NotConfigured, state.localNetworkAccessState)
        assertNull(state.pairedHost)
    }

    @Test
    fun restoredPairingKeepsGrantedNearbyState() {
        val repository = StubHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(privateLanPayload()),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 60L },
        )

        val state = repository.currentState()

        assertEquals(HostBridgeConnectionPhase.Paired, state.phase)
        assertEquals(LocalNetworkAccessState.Ready, state.localNetworkAccessState)
        assertEquals(true, state.nearbyWifiDevicesGranted)
    }

    private fun privateLanPayload(): String = """
        {
          "version": 1,
          "hostId": "host-main",
          "hostLabel": "Office Windows",
          "endpoint": "ws://192.168.0.24:4500",
          "transport": "ws",
          "sessionToken": "secret-token"
        }
    """.trimIndent()

    private fun publicEndpointPayload(): String = """
        {
          "version": 1,
          "hostId": "host-public",
          "hostLabel": "Cloud Tunnel",
          "endpoint": "wss://codex.example.com",
          "transport": "ws",
          "sessionToken": "secret-token"
        }
    """.trimIndent()
}
