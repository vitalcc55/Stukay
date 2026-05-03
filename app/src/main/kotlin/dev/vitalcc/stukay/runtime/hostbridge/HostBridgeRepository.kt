package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.HostBridgeConnectionPhase
import dev.vitalcc.stukay.core.model.HostBridgeConnectionState
import dev.vitalcc.stukay.core.model.LocalNetworkAccessState
import dev.vitalcc.stukay.core.model.PairingPayload

interface HostBridgeRepository {
    fun currentState(): HostBridgeConnectionState

    fun savePairingPayload(
        rawPayload: String,
        localNetworkPermissionGranted: Boolean,
    ): HostBridgeConnectionState

    fun connect(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState

    fun reconnect(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState

    fun refreshPermissionState(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState

    fun disconnect(clearPairing: Boolean = false): HostBridgeConnectionState
}

class StubHostBridgeRepository(
    private val pairingStore: HostBridgePairingStore,
    initialNearbyWifiDevicesGranted: Boolean,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) : HostBridgeRepository {
    private var savedPairingPayload: PairingPayload? = null
    private var state: HostBridgeConnectionState

    init {
        val restored = pairingStore.load()?.takeIf { it.isNotBlank() }
        if (restored == null) {
            state = emptyState()
        } else {
            val result = runCatching { parsePairingPayload(restored) }
            if (result.isSuccess) {
                savedPairingPayload = result.getOrThrow()
                state = pairedState(
                    payload = result.getOrThrow(),
                    phase = HostBridgeConnectionPhase.Paired,
                    localNetworkPermissionGranted = initialNearbyWifiDevicesGranted,
                )
            } else {
                pairingStore.clear()
                state = HostBridgeConnectionState(
                    phase = HostBridgeConnectionPhase.Failed,
                    nearbyWifiDevicesGranted = initialNearbyWifiDevicesGranted,
                    lastError = "Сохраненный pairing payload поврежден и был очищен.",
                    lastTransitionAtEpochMs = now(),
                )
            }
        }
    }

    override fun currentState(): HostBridgeConnectionState = state

    override fun savePairingPayload(
        rawPayload: String,
        localNetworkPermissionGranted: Boolean,
    ): HostBridgeConnectionState {
        val parsed = parsePairingPayload(rawPayload)
        pairingStore.save(rawPayload.trim())
        savedPairingPayload = parsed
        state = pairedState(
            payload = parsed,
            phase = HostBridgeConnectionPhase.Paired,
            localNetworkPermissionGranted = localNetworkPermissionGranted,
        )
        return state
    }

    override fun connect(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState {
        val payload = requireSavedPairing()
        val localNetworkState = resolveLocalNetworkAccessState(
            payload = payload,
            localNetworkPermissionGranted = localNetworkPermissionGranted,
        )
        state = when (localNetworkState) {
            LocalNetworkAccessState.UnsupportedForSlice -> HostBridgeConnectionState(
                phase = HostBridgeConnectionPhase.Failed,
                pairedHost = payload.toPairedHost(),
                localNetworkAccessState = localNetworkState,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = "Этот slice принимает только private LAN или .local endpoint. Публичный tunnel path вынесен в следующий milestone.",
                lastTransitionAtEpochMs = now(),
            )

            LocalNetworkAccessState.PermissionRequired -> HostBridgeConnectionState(
                phase = HostBridgeConnectionPhase.Connected,
                pairedHost = payload.toPairedHost(),
                localNetworkAccessState = localNetworkState,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = null,
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = now(),
            )

            LocalNetworkAccessState.Ready -> HostBridgeConnectionState(
                phase = HostBridgeConnectionPhase.Connected,
                pairedHost = payload.toPairedHost(),
                localNetworkAccessState = localNetworkState,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = null,
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = now(),
            )

            LocalNetworkAccessState.NotConfigured -> emptyState()
        }
        return state
    }

    override fun reconnect(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState = connect(
        localNetworkPermissionGranted = localNetworkPermissionGranted,
    )

    override fun refreshPermissionState(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState {
        val payload = savedPairingPayload ?: run {
            state = emptyState()
            return state
        }
        val localNetworkState = resolveLocalNetworkAccessState(
            payload = payload,
            localNetworkPermissionGranted = localNetworkPermissionGranted,
        )
        state = when (localNetworkState) {
            LocalNetworkAccessState.UnsupportedForSlice -> HostBridgeConnectionState(
                phase = HostBridgeConnectionPhase.Failed,
                pairedHost = payload.toPairedHost(),
                localNetworkAccessState = localNetworkState,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = "Этот slice принимает только private LAN или .local endpoint. Публичный tunnel path вынесен в следующий milestone.",
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
            )

            LocalNetworkAccessState.PermissionRequired -> HostBridgeConnectionState(
                phase = if (state.phase == HostBridgeConnectionPhase.Connected) {
                    HostBridgeConnectionPhase.Connected
                } else {
                    HostBridgeConnectionPhase.Paired
                },
                pairedHost = payload.toPairedHost(),
                localNetworkAccessState = localNetworkState,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = null,
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
            )

            LocalNetworkAccessState.Ready -> HostBridgeConnectionState(
                phase = if (state.phase == HostBridgeConnectionPhase.Connected) {
                    HostBridgeConnectionPhase.Connected
                } else {
                    HostBridgeConnectionPhase.Paired
                },
                pairedHost = payload.toPairedHost(),
                localNetworkAccessState = localNetworkState,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = null,
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
            )

            LocalNetworkAccessState.NotConfigured -> emptyState()
        }
        return state
    }

    override fun disconnect(clearPairing: Boolean): HostBridgeConnectionState {
        if (clearPairing) {
            pairingStore.clear()
            savedPairingPayload = null
            state = emptyState()
            return state
        }

        val payload = requireSavedPairing()
        state = pairedState(
            payload = payload,
            phase = HostBridgeConnectionPhase.Disconnected,
            localNetworkPermissionGranted = state.nearbyWifiDevicesGranted,
            lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
        )
        return state
    }

    private fun pairedState(
        payload: PairingPayload,
        phase: HostBridgeConnectionPhase,
        localNetworkPermissionGranted: Boolean,
        lastConnectedAtEpochMs: Long? = null,
    ): HostBridgeConnectionState = HostBridgeConnectionState(
        phase = phase,
        pairedHost = payload.toPairedHost(),
        localNetworkAccessState = resolveLocalNetworkAccessState(
            payload = payload,
            localNetworkPermissionGranted = localNetworkPermissionGranted,
        ),
        nearbyWifiDevicesGranted = localNetworkPermissionGranted,
        lastError = null,
        lastTransitionAtEpochMs = now(),
        lastConnectedAtEpochMs = lastConnectedAtEpochMs,
    )

    private fun resolveLocalNetworkAccessState(
        payload: PairingPayload,
        localNetworkPermissionGranted: Boolean,
    ): LocalNetworkAccessState {
        val host = endpointHostOrNull(payload.endpoint)
        if (host == null || !isSupportedLocalSliceHost(host)) {
            return LocalNetworkAccessState.UnsupportedForSlice
        }
        return LocalNetworkAccessState.Ready
    }

    private fun requireSavedPairing(): PairingPayload = requireNotNull(savedPairingPayload) {
        "Pairing payload еще не сохранен."
    }

    private fun emptyState(): HostBridgeConnectionState = HostBridgeConnectionState(
        phase = HostBridgeConnectionPhase.NotPaired,
        localNetworkAccessState = LocalNetworkAccessState.NotConfigured,
        nearbyWifiDevicesGranted = false,
        lastTransitionAtEpochMs = now(),
    )

    private fun now(): Long = timeProvider()
}

private fun isSupportedLocalSliceHost(host: String): Boolean {
    if (host.endsWith(".local")) {
        return true
    }

    val segments = host.split(".")
    if (segments.size != 4) {
        return false
    }

    val octets = segments.map { value -> value.toIntOrNull() ?: return false }
    val first = octets[0]
    val second = octets[1]
    return first == 10 ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168)
}
