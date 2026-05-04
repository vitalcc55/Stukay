package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.PairingPayload
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

interface HostBridgeClient {
    fun fetchRuntimeSummary(pairingPayload: PairingPayload): HostBridgeRuntimePayload
}

enum class HostBridgeClientStatus {
    Ready,
    Degraded,
    Unauthorized,
    Unreachable,
    Unknown,
}

enum class HostBridgeClientFailureCode {
    Unauthorized,
    Unavailable,
    Protocol,
}

class HostBridgeClientException(
    val failureCode: HostBridgeClientFailureCode,
    message: String,
) : IllegalStateException(message)

data class HostBridgeRuntimePayload(
    val hostStatus: HostBridgeClientStatus,
    val runtimeReady: Boolean,
    val appListCount: Int?,
    val lastRoundTripMs: Long?,
    val probeAtEpochMs: Long?,
    val retryAttempt: Int,
    val degradedReason: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val lastTransportError: String?,
) {
    companion object {
        fun ready(
            appListCount: Int,
            lastRoundTripMs: Long? = null,
            probeAtEpochMs: Long? = null,
        ): HostBridgeRuntimePayload = HostBridgeRuntimePayload(
            hostStatus = HostBridgeClientStatus.Ready,
            runtimeReady = true,
            appListCount = appListCount,
            lastRoundTripMs = lastRoundTripMs,
            probeAtEpochMs = probeAtEpochMs,
            retryAttempt = 0,
            degradedReason = null,
            errorCode = null,
            errorMessage = null,
            lastTransportError = null,
        )
    }
}

class OkHttpHostBridgeClient(
    private val okHttpClient: OkHttpClient = defaultHttpClient(),
) : HostBridgeClient {
    override fun fetchRuntimeSummary(pairingPayload: PairingPayload): HostBridgeRuntimePayload {
        val request = Request.Builder()
            .url(summaryUrl(pairingPayload.endpoint))
            .get()
            .header("Authorization", "Bearer ${pairingPayload.sessionToken}")
            .header("Accept", "application/json")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw mapFailure(
                        pairingPayload = pairingPayload,
                        statusCode = response.code,
                        bodyText = bodyText,
                    )
                }
                return parseSummaryPayload(
                    pairingPayload = pairingPayload,
                    bodyText = bodyText,
                )
            }
        } catch (error: HostBridgeClientException) {
            throw error
        } catch (error: IOException) {
            throw HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Unavailable,
                message = error.message ?: "Host Bridge helper недоступен.",
            )
        }
    }

    private fun mapFailure(
        pairingPayload: PairingPayload,
        statusCode: Int,
        bodyText: String,
    ): HostBridgeClientException {
        val fields = parseJsonObjectOrNull(bodyText)
        val errorCode = fields?.get("errorCode")?.trim()?.lowercase()
        val errorMessage = sanitizeRemoteDiagnosticText(
            text = fields?.get("errorMessage"),
            sessionToken = pairingPayload.sessionToken,
        )
            ?: "Host Bridge helper вернул HTTP $statusCode."
        return when {
            statusCode == 401 || errorCode == "unauthorized" -> HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Unauthorized,
                message = errorMessage,
            )

            statusCode in 300..399 -> HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Protocol,
                message = "Host Bridge helper redirect не разрешен для этого MVP.",
            )

            statusCode in setOf(408, 429, 500, 502, 503, 504) ||
                errorCode in setOf("timeout", "unavailable", "unreachable") -> HostBridgeClientException(
                    failureCode = HostBridgeClientFailureCode.Unavailable,
                    message = errorMessage,
                )

            else -> HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Protocol,
                message = errorMessage,
            )
        }
    }

    private fun parseSummaryPayload(
        pairingPayload: PairingPayload,
        bodyText: String,
    ): HostBridgeRuntimePayload {
        val fields = parseJsonObject(bodyText)
        return HostBridgeRuntimePayload(
            hostStatus = parseHostStatus(fields["hostStatus"]),
            runtimeReady = fields.requireBoolean("runtimeReady"),
            appListCount = fields.optionalInt("appListCount"),
            lastRoundTripMs = fields.optionalLong("lastRoundTripMs"),
            probeAtEpochMs = fields.optionalLong("probeAtEpochMs"),
            retryAttempt = fields.optionalInt("retryAttempt") ?: 0,
            degradedReason = sanitizeRemoteDiagnosticText(
                text = fields["degradedReason"],
                sessionToken = pairingPayload.sessionToken,
            ),
            errorCode = fields["errorCode"],
            errorMessage = sanitizeRemoteDiagnosticText(
                text = fields["errorMessage"],
                sessionToken = pairingPayload.sessionToken,
            ),
            lastTransportError = sanitizeRemoteDiagnosticText(
                text = fields["lastTransportError"],
                sessionToken = pairingPayload.sessionToken,
            ),
        )
    }

    private fun parseHostStatus(rawValue: String?): HostBridgeClientStatus = when (rawValue?.trim()?.lowercase()) {
        "ready" -> HostBridgeClientStatus.Ready
        "degraded" -> HostBridgeClientStatus.Degraded
        "unauthorized" -> HostBridgeClientStatus.Unauthorized
        "unreachable" -> HostBridgeClientStatus.Unreachable
        null, "" -> HostBridgeClientStatus.Unknown
        else -> HostBridgeClientStatus.Unknown
    }

    private fun summaryUrl(endpoint: String): String = endpoint.trimEnd('/') + SUMMARY_PATH

    private companion object {
        const val SUMMARY_PATH = "/v1/runtime/summary"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build()
    }
}

private fun parseJsonObject(rawBody: String): Map<String, String?> {
    val trimmed = rawBody.trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        throw HostBridgeClientException(
            failureCode = HostBridgeClientFailureCode.Protocol,
            message = "Host Bridge helper вернул некорректный JSON.",
        )
    }

    val fields = buildMap<String, String?> {
        JSON_FIELD_PATTERN.findAll(trimmed).forEach { match ->
            val key = match.groupValues[1]
            val rawValue = match.groupValues[2]
            val value = when {
                rawValue == "null" -> null
                rawValue.startsWith('"') -> rawValue
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                else -> rawValue
            }
            put(key, value)
        }
    }

    if (fields.isEmpty()) {
        throw HostBridgeClientException(
            failureCode = HostBridgeClientFailureCode.Protocol,
            message = "Host Bridge helper вернул пустой JSON object.",
        )
    }
    return fields
}

private fun parseJsonObjectOrNull(rawBody: String): Map<String, String?>? = runCatching {
    parseJsonObject(rawBody)
}.getOrNull()

private fun Map<String, String?>.requireBoolean(name: String): Boolean = when (this[name]?.trim()?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> throw HostBridgeClientException(
        failureCode = HostBridgeClientFailureCode.Protocol,
        message = "Host Bridge helper не прислал корректное поле $name.",
    )
}

private fun Map<String, String?>.optionalInt(name: String): Int? = this[name]?.toIntOrNull()

private fun Map<String, String?>.optionalLong(name: String): Long? = this[name]?.toLongOrNull()

private fun sanitizeRemoteDiagnosticText(
    text: String?,
    sessionToken: String,
): String? {
    val trimmed = text?.takeIf { it.isNotBlank() } ?: return null
    return trimmed
        .replace(AUTHORIZATION_BEARER_PATTERN, "Authorization: Bearer <redacted>")
        .replace(BARE_BEARER_PATTERN, "Bearer <redacted>")
        .replace(sessionToken, "<redacted>")
}

private val JSON_FIELD_PATTERN = Regex(
    "\"([^\"]+)\"\\s*:\\s*(null|true|false|-?\\d+|\"(?:\\\\.|[^\"])*\")",
)
private val AUTHORIZATION_BEARER_PATTERN = Regex("(?i)authorization\\s*:\\s*bearer\\s+\\S+")
private val BARE_BEARER_PATTERN = Regex("(?i)bearer\\s+\\S+")
