package dev.vitalcc.stukay.core.model

import java.net.URI

@JvmInline
value class HostId(val value: String)

enum class HostBridgeTransport {
    HttpJson,
}

enum class LocalNetworkAccessState {
    NotConfigured,
    Ready,
    UnsupportedForSlice,
}

enum class HostBridgeConnectionPhase {
    NotPaired,
    Paired,
    Connecting,
    Connected,
    Degraded,
    Disconnected,
    Failed,
}

enum class HostRuntimeStatus {
    Unknown,
    Ready,
    Degraded,
    Unauthorized,
    Unreachable,
}

enum class HostRuntimeSnapshotScope {
    None,
    Live,
    LastKnown,
}

data class PairedHost(
    val hostId: HostId,
    val hostLabel: String,
    val endpoint: String,
    val transport: HostBridgeTransport,
)

data class PairingPayload(
    val version: Int,
    val hostId: HostId,
    val hostLabel: String,
    val endpoint: String,
    val transport: HostBridgeTransport,
    val sessionToken: String,
) {
    fun toPairedHost(): PairedHost = PairedHost(
        hostId = hostId,
        hostLabel = hostLabel,
        endpoint = endpoint,
        transport = transport,
    )

    override fun toString(): String = "PairingPayload(" +
        "version=$version, " +
        "hostId=${hostId.value}, " +
        "hostLabel=$hostLabel, " +
        "endpoint=$endpoint, " +
        "transport=$transport, " +
        "sessionToken=<redacted>)"
}

data class HostRuntimeSummary(
    val hostStatus: HostRuntimeStatus = HostRuntimeStatus.Unknown,
    val runtimeReady: Boolean = false,
    val appListCount: Int? = null,
    val lastRoundTripMs: Long? = null,
    val lastProbeAtEpochMs: Long? = null,
    val retryAttempt: Int = 0,
    val degradedReason: String? = null,
    val lastTransportError: String? = null,
)

data class HostBridgeConnectionState(
    val phase: HostBridgeConnectionPhase,
    val pairedHost: PairedHost? = null,
    val runtimeSummary: HostRuntimeSummary = HostRuntimeSummary(),
    val localNetworkAccessState: LocalNetworkAccessState = LocalNetworkAccessState.NotConfigured,
    val nearbyWifiDevicesGranted: Boolean = false,
    val lastError: String? = null,
    val lastTransitionAtEpochMs: Long = 0L,
    val lastConnectedAtEpochMs: Long? = null,
)

fun HostBridgeConnectionState.runtimeSummaryScope(): HostRuntimeSnapshotScope = when {
    runtimeSummary.isEmpty() -> HostRuntimeSnapshotScope.None
    phase == HostBridgeConnectionPhase.Connected || phase == HostBridgeConnectionPhase.Degraded ->
        HostRuntimeSnapshotScope.Live
    phase == HostBridgeConnectionPhase.Failed && runtimeSummary.hostStatus in setOf(
        HostRuntimeStatus.Unauthorized,
        HostRuntimeStatus.Unreachable,
        HostRuntimeStatus.Unknown,
    ) -> HostRuntimeSnapshotScope.Live
    else -> HostRuntimeSnapshotScope.LastKnown
}

private fun HostRuntimeSummary.isEmpty(): Boolean = hostStatus == HostRuntimeStatus.Unknown &&
    !runtimeReady &&
    appListCount == null &&
    lastRoundTripMs == null &&
    lastProbeAtEpochMs == null &&
    retryAttempt == 0 &&
    degradedReason == null &&
    lastTransportError == null

fun hostBridgeEndpointDisplayValue(endpoint: String): String = runCatching {
    val uri = URI(endpoint)
    buildString {
        append(uri.scheme)
        append("://")
        append(uri.host)
        if (uri.port >= 0) {
            append(":")
            append(uri.port)
        }
        val normalizedPath = uri.path.orEmpty()
        if (normalizedPath.isNotBlank() && normalizedPath != "/") {
            append(normalizedPath)
        }
    }
}.getOrElse { endpoint }
