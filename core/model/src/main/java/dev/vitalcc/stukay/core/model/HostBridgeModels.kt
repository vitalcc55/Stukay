package dev.vitalcc.stukay.core.model

import java.net.URI

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
    val nearbyWifiDevicesGranted: Boolean = false,
    val lastError: String? = null,
    val lastTransitionAtEpochMs: Long = 0L,
    val lastConnectedAtEpochMs: Long? = null,
)

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
