package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.HostBridgeTransport
import dev.vitalcc.stukay.core.model.HostId
import dev.vitalcc.stukay.core.model.PairingPayload
import java.net.URI

fun parsePairingPayload(rawInput: String): PairingPayload {
    val trimmed = rawInput.trim()
    require(trimmed.isNotEmpty()) { "Вставьте pairing payload перед сохранением." }

    val fields = buildFieldMap(trimmed)
    if (fields.isEmpty()) {
        throw IllegalArgumentException("Ожидается JSON pairing payload.")
    }

    val version = fields["version"]?.toIntOrNull() ?: fields["v"]?.toIntOrNull() ?: 1
    val hostId = (fields["hostId"] ?: fields["sessionId"]).orEmpty().trim()
    val hostLabel = (fields["hostLabel"] ?: fields["relay"] ?: "Windows Host Bridge").trim()
    val endpoint = (fields["endpoint"] ?: fields["relay"]).orEmpty().trim()
    val transport = parseTransport(fields["transport"] ?: fields["mode"] ?: "ws")
    val sessionToken = (fields["sessionToken"] ?: fields["token"]).orEmpty().trim()

    require(hostId.isNotEmpty()) { "Pairing payload не содержит hostId." }
    require(hostLabel.isNotEmpty()) { "Pairing payload не содержит hostLabel." }
    require(endpoint.isNotEmpty()) { "Pairing payload не содержит endpoint." }
    require(sessionToken.isNotEmpty()) { "Pairing payload не содержит sessionToken." }
    validateEndpoint(endpoint)

    return PairingPayload(
        version = version,
        hostId = HostId(hostId),
        hostLabel = hostLabel,
        endpoint = endpoint,
        transport = transport,
        sessionToken = sessionToken,
    )
}

fun endpointHostOrNull(endpoint: String): String? = runCatching {
    URI(endpoint).host?.lowercase()
}.getOrNull()

private fun parseTransport(rawValue: String): HostBridgeTransport = when (rawValue.trim().lowercase()) {
    "http", "http_json" -> HostBridgeTransport.HttpJson
    "ws", "wss", "websocket", "ws_jsonrpc", "jsonrpc_ws" -> HostBridgeTransport.WebSocketJsonRpc
    else -> throw IllegalArgumentException("Pairing payload содержит неподдерживаемый transport: $rawValue")
}

private fun validateEndpoint(endpoint: String) {
    val uri = try {
        URI(endpoint)
    } catch (error: Exception) {
        throw IllegalArgumentException("Pairing payload содержит некорректный endpoint.", error)
    }

    val scheme = uri.scheme?.lowercase().orEmpty()
    require(scheme in setOf("http", "https", "ws", "wss")) {
        "Endpoint должен использовать http/https/ws/wss."
    }
    require(!uri.host.isNullOrBlank()) { "Endpoint должен содержать host." }
}

private fun buildFieldMap(payload: String): Map<String, String> {
    val fieldPattern = Regex("\"([^\"]+)\"\\s*:\\s*(\"(?:\\\\.|[^\"])*\"|-?\\d+)")
    return buildMap {
        fieldPattern.findAll(payload).forEach { match ->
            val key = match.groupValues[1]
            val rawValue = match.groupValues[2]
            val value = if (rawValue.startsWith('"')) {
                rawValue
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } else {
                rawValue
            }
            put(key, value)
        }
    }
}
