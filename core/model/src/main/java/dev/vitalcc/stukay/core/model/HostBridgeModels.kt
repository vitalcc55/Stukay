package dev.vitalcc.stukay.core.model

@JvmInline
value class HostId(val value: String)

enum class HostBridgeTransport {
    HttpJson,
    WebSocketJsonRpc,
}

enum class LocalNetworkAccessState {
    NotConfigured,
    Ready,
    PermissionRequired,
    UnsupportedForSlice,
}

enum class HostBridgeConnectionPhase {
    NotPaired,
    Paired,
    Connecting,
    Connected,
    Disconnected,
    PermissionRequired,
    Failed,
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

data class HostBridgeConnectionState(
    val phase: HostBridgeConnectionPhase,
    val pairedHost: PairedHost? = null,
    val localNetworkAccessState: LocalNetworkAccessState = LocalNetworkAccessState.NotConfigured,
    val lastError: String? = null,
    val lastTransitionAtEpochMs: Long = 0L,
    val lastConnectedAtEpochMs: Long? = null,
)
