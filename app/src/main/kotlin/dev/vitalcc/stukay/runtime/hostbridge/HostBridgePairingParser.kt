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
    val transport = parseTransport(fields["transport"] ?: fields["mode"] ?: "http_json")
    val sessionToken = (fields["sessionToken"] ?: fields["token"]).orEmpty().trim()

    require(hostId.isNotEmpty()) { "Pairing payload не содержит hostId." }
    require(hostLabel.isNotEmpty()) { "Pairing payload не содержит hostLabel." }
    require(endpoint.isNotEmpty()) { "Pairing payload не содержит endpoint." }
    require(sessionToken.isNotEmpty()) { "Pairing payload не содержит sessionToken." }
    validateEndpoint(endpoint, transport)

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
    "ws", "wss", "websocket", "ws_jsonrpc", "jsonrpc_ws" -> throw IllegalArgumentException(
        "Transport ws/wss не поддерживается в Host Bridge MVP. Используйте http_json endpoint.",
    )
    else -> throw IllegalArgumentException("Pairing payload содержит неподдерживаемый transport: $rawValue")
}

private fun validateEndpoint(
    endpoint: String,
    transport: HostBridgeTransport,
) {
    val uri = try {
        URI(endpoint)
    } catch (error: Exception) {
        throw IllegalArgumentException("Pairing payload содержит некорректный endpoint.", error)
    }

    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme in setOf("ws", "wss")) {
        throw IllegalArgumentException("Transport ws/wss не поддерживается в Host Bridge MVP. Используйте http_json endpoint.")
    }
    require(scheme in setOf("http", "https")) {
        "Endpoint должен использовать http/https."
    }
    require(!uri.host.isNullOrBlank()) { "Endpoint должен содержать host." }
    val normalizedPath = uri.path.orEmpty()
    require(normalizedPath.isBlank() || normalizedPath == "/") {
        "Endpoint должен быть base host endpoint без route path; client сам добавляет /v1/runtime/summary."
    }
    require(uri.userInfo.isNullOrBlank()) { "Endpoint не должен содержать embedded credentials." }
    require(uri.query.isNullOrBlank()) { "Endpoint не должен содержать query secrets." }
    require(uri.fragment.isNullOrBlank()) { "Endpoint не должен содержать fragment." }
    val endpointTransport = HostBridgeTransport.HttpJson
    require(transport == endpointTransport) {
        "Endpoint transport scheme должен совпадать с transport."
    }
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
