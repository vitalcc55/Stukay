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
        assertEquals(LocalNetworkAccessState.PermissionRequired, state.localNetworkAccessState)
        assertEquals("Office Windows", state.pairedHost?.hostLabel)
    }

    @Test
    fun connectKeepsPairedStateWhenPermissionIsMissing() {
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

        assertEquals(HostBridgeConnectionPhase.Paired, state.phase)
        assertEquals(LocalNetworkAccessState.PermissionRequired, state.localNetworkAccessState)
        assertEquals(null, state.lastConnectedAtEpochMs)
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
    fun connectAcceptsCarrierGradeNatWhenPermissionIsGranted() {
        val repository = StubHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
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
    fun linkLocalEndpointIsRejectedForCurrentSlice() {
        val repository = StubHostBridgeRepository(
            pairingStore = InMemoryHostBridgePairingStore(),
            initialNearbyWifiDevicesGranted = true,
            timeProvider = { 45L },
        )
        repository.savePairingPayload(
            rawPayload = linkLocalPayload(),
            localNetworkPermissionGranted = true,
        )

        val state = repository.connect(localNetworkPermissionGranted = true)

        assertEquals(HostBridgeConnectionPhase.Failed, state.phase)
        assertEquals(LocalNetworkAccessState.UnsupportedForSlice, state.localNetworkAccessState)
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

    private fun publicEndpointPayload(): String = """
        {
          "version": 1,
          "hostId": "host-public",
          "hostLabel": "Cloud Tunnel",
          "endpoint": "https://codex.example.com",
          "transport": "http_json",
          "sessionToken": "secret-token"
        }
    """.trimIndent()
}
