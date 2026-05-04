package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.HostBridgeConnectionPhase
import dev.vitalcc.stukay.core.model.HostBridgeConnectionState
import dev.vitalcc.stukay.core.model.HostRuntimeStatus
import dev.vitalcc.stukay.core.model.HostRuntimeSummary
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

    fun probe(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState

    fun refreshPermissionState(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState

    fun disconnect(clearPairing: Boolean = false): HostBridgeConnectionState

    fun listThreads(): HostBridgeThreadListPayload

    fun readThread(threadId: String): HostBridgeThreadPayload

    fun resumeThread(threadId: String): HostBridgeThreadPayload

    fun startTurn(threadId: String, text: String): HostBridgeTurnPayload

    fun interruptTurn(threadId: String, turnId: String)

    fun respondToApproval(requestId: String, decision: ApprovalDecision)

    fun openThreadEventStream(threadId: String): HostBridgeEventStream
}

class HttpJsonHostBridgeRepository(
    private val pairingStore: HostBridgePairingStore,
    private val client: HostBridgeClient,
    initialNearbyWifiDevicesGranted: Boolean,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) : HostBridgeRepository {
    private var savedPairingPayload: PairingPayload? = null
    private var consecutiveProbeFailures: Int = 0
    private var state: HostBridgeConnectionState

    init {
        val restored = pairingStore.load()?.takeIf { it.isNotBlank() }
        state = if (restored == null) {
            emptyState(nearbyWifiDevicesGranted = initialNearbyWifiDevicesGranted)
        } else {
            val result = runCatching { parsePairingPayload(restored) }
            if (result.isSuccess) {
                val payload = result.getOrThrow()
                savedPairingPayload = payload
                pairedState(
                    payload = payload,
                    phase = HostBridgeConnectionPhase.Paired,
                    localNetworkPermissionGranted = initialNearbyWifiDevicesGranted,
                )
            } else {
                pairingStore.clear()
                HostBridgeConnectionState(
                    phase = HostBridgeConnectionPhase.Failed,
                    nearbyWifiDevicesGranted = initialNearbyWifiDevicesGranted,
                    lastError = "Сохраненный pairing payload поврежден и был очищен.",
                    lastTransitionAtEpochMs = now(),
                )
            }
        }
    }

    @Synchronized
    override fun currentState(): HostBridgeConnectionState = state

    @Synchronized
    override fun savePairingPayload(
        rawPayload: String,
        localNetworkPermissionGranted: Boolean,
    ): HostBridgeConnectionState {
        val parsed = parsePairingPayload(rawPayload)
        pairingStore.save(rawPayload.trim())
        savedPairingPayload = parsed
        consecutiveProbeFailures = 0
        state = pairedState(
            payload = parsed,
            phase = HostBridgeConnectionPhase.Paired,
            localNetworkPermissionGranted = localNetworkPermissionGranted,
        )
        return state
    }

    @Synchronized
    override fun connect(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState = fetchRuntimeState(
        localNetworkPermissionGranted = localNetworkPermissionGranted,
        trigger = RuntimeTrigger.Connect,
    )

    @Synchronized
    override fun reconnect(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState = fetchRuntimeState(
        localNetworkPermissionGranted = localNetworkPermissionGranted,
        trigger = RuntimeTrigger.Reconnect,
    )

    @Synchronized
    override fun probe(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState = fetchRuntimeState(
        localNetworkPermissionGranted = localNetworkPermissionGranted,
        trigger = RuntimeTrigger.Probe,
    )

    @Synchronized
    override fun refreshPermissionState(localNetworkPermissionGranted: Boolean): HostBridgeConnectionState {
        val payload = savedPairingPayload ?: run {
            state = emptyState(nearbyWifiDevicesGranted = localNetworkPermissionGranted)
            return state
        }
        val localNetworkState = resolveLocalNetworkAccessState(payload)
        state = when (localNetworkState) {
            LocalNetworkAccessState.UnsupportedForSlice -> failedSliceState(
                payload = payload,
                localNetworkPermissionGranted = localNetworkPermissionGranted,
            )

            LocalNetworkAccessState.Ready -> HostBridgeConnectionState(
                phase = when (state.phase) {
                    HostBridgeConnectionPhase.Failed,
                    HostBridgeConnectionPhase.Connecting,
                    HostBridgeConnectionPhase.Connected,
                    HostBridgeConnectionPhase.Degraded,
                    HostBridgeConnectionPhase.Disconnected,
                    -> state.phase

                    else -> HostBridgeConnectionPhase.Paired
                },
                pairedHost = payload.toPairedHost(),
                runtimeSummary = state.runtimeSummary,
                localNetworkAccessState = localNetworkState,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = if (state.phase == HostBridgeConnectionPhase.Paired) {
                    null
                } else {
                    state.lastError
                },
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
            )

            LocalNetworkAccessState.NotConfigured -> emptyState(nearbyWifiDevicesGranted = localNetworkPermissionGranted)
        }
        return state
    }

    @Synchronized
    override fun disconnect(clearPairing: Boolean): HostBridgeConnectionState {
        if (clearPairing) {
            pairingStore.clear()
            savedPairingPayload = null
            consecutiveProbeFailures = 0
            state = emptyState(nearbyWifiDevicesGranted = state.nearbyWifiDevicesGranted)
            return state
        }

        val payload = requireSavedPairing()
        consecutiveProbeFailures = 0
        state = HostBridgeConnectionState(
            phase = HostBridgeConnectionPhase.Disconnected,
            pairedHost = payload.toPairedHost(),
            runtimeSummary = state.runtimeSummary.copy(retryAttempt = 0),
            localNetworkAccessState = resolveLocalNetworkAccessState(payload),
            nearbyWifiDevicesGranted = state.nearbyWifiDevicesGranted,
            lastError = null,
            lastTransitionAtEpochMs = now(),
            lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
        )
        return state
    }

    @Synchronized
    override fun listThreads(): HostBridgeThreadListPayload = client.listThreads(requireRuntimePayload())

    @Synchronized
    override fun readThread(threadId: String): HostBridgeThreadPayload = client.readThread(
        pairingPayload = requireRuntimePayload(),
        threadId = threadId,
    )

    @Synchronized
    override fun resumeThread(threadId: String): HostBridgeThreadPayload = client.resumeThread(
        pairingPayload = requireRuntimePayload(),
        threadId = threadId,
    )

    @Synchronized
    override fun startTurn(threadId: String, text: String): HostBridgeTurnPayload = client.startTurn(
        pairingPayload = requireRuntimePayload(),
        threadId = threadId,
        text = text,
    )

    @Synchronized
    override fun interruptTurn(threadId: String, turnId: String) {
        client.interruptTurn(
            pairingPayload = requireRuntimePayload(),
            threadId = threadId,
            turnId = turnId,
        )
    }

    @Synchronized
    override fun respondToApproval(requestId: String, decision: ApprovalDecision) {
        client.respondToApproval(
            pairingPayload = requireRuntimePayload(),
            requestId = requestId,
            decision = decision,
        )
    }

    @Synchronized
    override fun openThreadEventStream(threadId: String): HostBridgeEventStream = client.openThreadEventStream(
        pairingPayload = requireRuntimePayload(),
        threadId = threadId,
    )

    private fun fetchRuntimeState(
        localNetworkPermissionGranted: Boolean,
        trigger: RuntimeTrigger,
    ): HostBridgeConnectionState {
        val payload = requireSavedPairing()
        val localNetworkState = resolveLocalNetworkAccessState(payload)
        when (localNetworkState) {
            LocalNetworkAccessState.UnsupportedForSlice -> {
                state = failedSliceState(payload, localNetworkPermissionGranted)
                return state
            }

            LocalNetworkAccessState.NotConfigured -> {
                state = emptyState(nearbyWifiDevicesGranted = localNetworkPermissionGranted)
                return state
            }

            LocalNetworkAccessState.Ready -> Unit
        }

        return try {
            val payloadState = client.fetchRuntimeSummary(payload)
            consecutiveProbeFailures = if (payloadState.hostStatus == HostBridgeClientStatus.Ready && payloadState.runtimeReady) {
                0
            } else {
                maxOf(1, payloadState.retryAttempt)
            }
            state = mapRuntimePayload(
                pairingPayload = payload,
                runtimePayload = payloadState,
                localNetworkPermissionGranted = localNetworkPermissionGranted,
                trigger = trigger,
            )
            state
        } catch (error: HostBridgeClientException) {
            state = mapClientFailure(
                pairingPayload = payload,
                localNetworkPermissionGranted = localNetworkPermissionGranted,
                error = error,
            )
            state
        }
    }

    private fun mapRuntimePayload(
        pairingPayload: PairingPayload,
        runtimePayload: HostBridgeRuntimePayload,
        localNetworkPermissionGranted: Boolean,
        trigger: RuntimeTrigger,
    ): HostBridgeConnectionState {
        val baseSummary = state.runtimeSummary
        return when {
            runtimePayload.hostStatus == HostBridgeClientStatus.Unauthorized -> HostBridgeConnectionState(
                phase = HostBridgeConnectionPhase.Failed,
                pairedHost = pairingPayload.toPairedHost(),
                runtimeSummary = HostRuntimeSummary(
                    hostStatus = HostRuntimeStatus.Unauthorized,
                    runtimeReady = false,
                    retryAttempt = 0,
                    degradedReason = null,
                    lastTransportError = runtimePayload.errorMessage ?: runtimePayload.lastTransportError,
                ),
                localNetworkAccessState = LocalNetworkAccessState.Ready,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = runtimePayload.errorMessage ?: "Host Bridge helper отклонил session token.",
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
            )

            runtimePayload.hostStatus == HostBridgeClientStatus.Ready && runtimePayload.runtimeReady -> HostBridgeConnectionState(
                phase = HostBridgeConnectionPhase.Connected,
                pairedHost = pairingPayload.toPairedHost(),
                runtimeSummary = HostRuntimeSummary(
                    hostStatus = HostRuntimeStatus.Ready,
                    runtimeReady = true,
                    appListCount = runtimePayload.appListCount,
                    lastRoundTripMs = runtimePayload.lastRoundTripMs,
                    lastProbeAtEpochMs = runtimePayload.probeAtEpochMs ?: now(),
                    retryAttempt = 0,
                    degradedReason = null,
                    lastTransportError = null,
                ),
                localNetworkAccessState = LocalNetworkAccessState.Ready,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = null,
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = now(),
            )

            else -> {
                val mergedSummary = mergeDegradedSummary(
                    previous = baseSummary,
                    fallbackStatus = when (runtimePayload.hostStatus) {
                        HostBridgeClientStatus.Unreachable -> HostRuntimeStatus.Unreachable
                        else -> HostRuntimeStatus.Degraded
                    },
                    runtimePayload = runtimePayload,
                    retryAttempt = maxOf(1, consecutiveProbeFailures),
                )
                HostBridgeConnectionState(
                    phase = HostBridgeConnectionPhase.Degraded,
                    pairedHost = pairingPayload.toPairedHost(),
                    runtimeSummary = mergedSummary,
                    localNetworkAccessState = LocalNetworkAccessState.Ready,
                    nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                    lastError = runtimePayload.errorMessage
                        ?: runtimePayload.degradedReason
                        ?: runtimePayload.lastTransportError
                        ?: when (trigger) {
                            RuntimeTrigger.Probe -> "Probe к Host Bridge helper завершился degraded response."
                            RuntimeTrigger.Connect -> "Подключение к Host Bridge helper вернуло degraded response."
                            RuntimeTrigger.Reconnect -> "Повторное подключение к Host Bridge helper вернуло degraded response."
                        },
                    lastTransitionAtEpochMs = now(),
                    lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
                )
            }
        }
    }

    private fun mapClientFailure(
        pairingPayload: PairingPayload,
        localNetworkPermissionGranted: Boolean,
        error: HostBridgeClientException,
    ): HostBridgeConnectionState = when (error.failureCode) {
        HostBridgeClientFailureCode.Unauthorized -> HostBridgeConnectionState(
            phase = HostBridgeConnectionPhase.Failed,
            pairedHost = pairingPayload.toPairedHost(),
            runtimeSummary = HostRuntimeSummary(
                hostStatus = HostRuntimeStatus.Unauthorized,
                runtimeReady = false,
                retryAttempt = 0,
                degradedReason = null,
                lastTransportError = error.message,
            ),
            localNetworkAccessState = LocalNetworkAccessState.Ready,
            nearbyWifiDevicesGranted = localNetworkPermissionGranted,
            lastError = error.message,
            lastTransitionAtEpochMs = now(),
            lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
        )

        HostBridgeClientFailureCode.Unavailable -> {
            consecutiveProbeFailures += 1
            val publicMessage = unavailableFailureMessage(
                pairingPayload = pairingPayload,
                localNetworkPermissionGranted = localNetworkPermissionGranted,
                fallbackMessage = error.message,
            )
            HostBridgeConnectionState(
                phase = HostBridgeConnectionPhase.Degraded,
                pairedHost = pairingPayload.toPairedHost(),
                runtimeSummary = mergeDegradedSummary(
                    previous = state.runtimeSummary,
                    fallbackStatus = if (state.runtimeSummary.appListCount == null && !state.runtimeSummary.runtimeReady) {
                        HostRuntimeStatus.Unreachable
                    } else {
                        HostRuntimeStatus.Degraded
                    },
                    runtimePayload = null,
                    retryAttempt = consecutiveProbeFailures,
                    transportMessage = publicMessage,
                ),
                localNetworkAccessState = LocalNetworkAccessState.Ready,
                nearbyWifiDevicesGranted = localNetworkPermissionGranted,
                lastError = publicMessage,
                lastTransitionAtEpochMs = now(),
                lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
            )
        }

        HostBridgeClientFailureCode.Protocol -> HostBridgeConnectionState(
            phase = HostBridgeConnectionPhase.Failed,
            pairedHost = pairingPayload.toPairedHost(),
            runtimeSummary = HostRuntimeSummary(
                hostStatus = HostRuntimeStatus.Unknown,
                runtimeReady = false,
                retryAttempt = consecutiveProbeFailures,
                degradedReason = null,
                lastTransportError = error.message,
            ),
            localNetworkAccessState = LocalNetworkAccessState.Ready,
            nearbyWifiDevicesGranted = localNetworkPermissionGranted,
            lastError = error.message,
            lastTransitionAtEpochMs = now(),
            lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
        )
    }

    private fun mergeDegradedSummary(
        previous: HostRuntimeSummary,
        fallbackStatus: HostRuntimeStatus,
        runtimePayload: HostBridgeRuntimePayload?,
        retryAttempt: Int,
        transportMessage: String? = null,
    ): HostRuntimeSummary = HostRuntimeSummary(
        hostStatus = fallbackStatus,
        runtimeReady = runtimePayload?.runtimeReady ?: false,
        appListCount = runtimePayload?.appListCount ?: previous.appListCount,
        lastRoundTripMs = runtimePayload?.lastRoundTripMs ?: previous.lastRoundTripMs,
        lastProbeAtEpochMs = runtimePayload?.probeAtEpochMs ?: now(),
        retryAttempt = retryAttempt,
        degradedReason = runtimePayload?.degradedReason ?: previous.degradedReason ?: transportMessage,
        lastTransportError = runtimePayload?.lastTransportError
            ?: runtimePayload?.errorMessage
            ?: transportMessage
            ?: previous.lastTransportError,
    )

    private fun failedSliceState(
        payload: PairingPayload,
        localNetworkPermissionGranted: Boolean,
    ): HostBridgeConnectionState = HostBridgeConnectionState(
        phase = HostBridgeConnectionPhase.Failed,
        pairedHost = payload.toPairedHost(),
        runtimeSummary = state.runtimeSummary.copy(retryAttempt = 0),
        localNetworkAccessState = LocalNetworkAccessState.UnsupportedForSlice,
        nearbyWifiDevicesGranted = localNetworkPermissionGranted,
        lastError = "Этот slice принимает только private LAN или .local endpoint. Публичный tunnel path вынесен в следующий milestone.",
        lastTransitionAtEpochMs = now(),
        lastConnectedAtEpochMs = state.lastConnectedAtEpochMs,
    )

    private fun pairedState(
        payload: PairingPayload,
        phase: HostBridgeConnectionPhase,
        localNetworkPermissionGranted: Boolean,
        lastConnectedAtEpochMs: Long? = null,
    ): HostBridgeConnectionState = HostBridgeConnectionState(
        phase = phase,
        pairedHost = payload.toPairedHost(),
        localNetworkAccessState = resolveLocalNetworkAccessState(payload),
        nearbyWifiDevicesGranted = localNetworkPermissionGranted,
        lastError = null,
        lastTransitionAtEpochMs = now(),
        lastConnectedAtEpochMs = lastConnectedAtEpochMs,
    )

    private fun resolveLocalNetworkAccessState(payload: PairingPayload): LocalNetworkAccessState {
        val host = endpointHostOrNull(payload.endpoint)
        if (host == null || !isSupportedLocalSliceHost(host)) {
            return LocalNetworkAccessState.UnsupportedForSlice
        }
        return LocalNetworkAccessState.Ready
    }

    private fun requireRuntimePayload(): PairingPayload {
        val payload = requireSavedPairing()
        check(resolveLocalNetworkAccessState(payload) == LocalNetworkAccessState.Ready) {
            "Host Bridge runtime path сейчас недоступен для этого endpoint."
        }
        return payload
    }

    private fun unavailableFailureMessage(
        pairingPayload: PairingPayload,
        localNetworkPermissionGranted: Boolean,
        fallbackMessage: String?,
    ): String {
        val baseMessage = fallbackMessage ?: "Host Bridge helper недоступен."
        if (localNetworkPermissionGranted) {
            return baseMessage
        }
        val host = endpointHostOrNull(pairingPayload.endpoint)
        if (host == null || !isSupportedLocalSliceHost(host)) {
            return baseMessage
        }
        return "$baseMessage. Если на устройстве включен Android 16 local-network opt-in, выдайте Nearby devices."
    }

    private fun requireSavedPairing(): PairingPayload = requireNotNull(savedPairingPayload) {
        "Pairing payload еще не сохранен."
    }

    private fun emptyState(
        nearbyWifiDevicesGranted: Boolean,
    ): HostBridgeConnectionState = HostBridgeConnectionState(
        phase = HostBridgeConnectionPhase.NotPaired,
        localNetworkAccessState = LocalNetworkAccessState.NotConfigured,
        nearbyWifiDevicesGranted = nearbyWifiDevicesGranted,
        lastTransitionAtEpochMs = now(),
    )

    private fun now(): Long = timeProvider()
}

private enum class RuntimeTrigger {
    Connect,
    Reconnect,
    Probe,
}

internal fun isSupportedLocalSliceHost(host: String): Boolean {
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
        (first == 100 && second in 64..127) ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168)
}
