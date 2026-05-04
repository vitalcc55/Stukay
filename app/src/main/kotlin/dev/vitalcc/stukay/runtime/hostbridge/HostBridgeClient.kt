package dev.vitalcc.stukay.runtime.hostbridge

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.PairingPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

interface HostBridgeClient {
    fun fetchRuntimeSummary(pairingPayload: PairingPayload): HostBridgeRuntimePayload

    fun listThreads(pairingPayload: PairingPayload): HostBridgeThreadListPayload

    fun readThread(pairingPayload: PairingPayload, threadId: String): HostBridgeThreadPayload

    fun resumeThread(pairingPayload: PairingPayload, threadId: String): HostBridgeThreadPayload

    fun startTurn(
        pairingPayload: PairingPayload,
        threadId: String,
        text: String,
    ): HostBridgeTurnPayload

    fun interruptTurn(
        pairingPayload: PairingPayload,
        threadId: String,
        turnId: String,
    )

    fun respondToApproval(
        pairingPayload: PairingPayload,
        requestId: String,
        decision: ApprovalDecision,
    )

    fun openThreadEventStream(
        pairingPayload: PairingPayload,
        threadId: String,
    ): HostBridgeEventStream
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

data class HostBridgeThreadListPayload(
    val data: List<HostBridgeThreadPayload>,
)

data class HostBridgeThreadStatusPayload(
    val type: String,
    val activeFlags: List<String>,
)

data class HostBridgeThreadPayload(
    val id: String,
    val cwd: String,
    val title: String,
    val preview: String,
    val sourceKind: String,
    val updatedAtEpochMs: Long?,
    val createdAtEpochMs: Long?,
    val turnCount: Int,
    val status: HostBridgeThreadStatusPayload,
    val timeline: List<HostBridgeTimelineItemPayload> = emptyList(),
)

data class HostBridgeTurnPayload(
    val id: String,
    val status: String,
    val startedAtEpochMs: Long?,
    val completedAtEpochMs: Long?,
    val durationMs: Long?,
    val errorMessage: String?,
)

data class HostBridgeTimelineItemPayload(
    val type: String,
    val id: String,
    val threadId: String,
    val turnId: String? = null,
    val itemId: String? = null,
    val text: String? = null,
    val streaming: Boolean = false,
    val phase: String? = null,
    val commandPreview: String? = null,
    val status: String? = null,
    val cwd: String? = null,
    val aggregatedOutput: String? = null,
    val exitCode: Int? = null,
    val path: String? = null,
    val changeKind: String? = null,
    val title: String? = null,
    val detail: String? = null,
)

data class HostBridgeApprovalPayload(
    val id: String,
    val requestId: String,
    val itemId: String,
    val threadId: String,
    val turnId: String,
    val kind: String,
    val title: String,
    val description: String,
    val availableDecisions: List<String>,
    val command: String? = null,
    val cwd: String? = null,
    val grantRoot: String? = null,
    val networkHost: String? = null,
    val networkProtocol: String? = null,
    val reason: String? = null,
)

data class HostBridgeThreadEvent(
    val method: String,
    val threadId: String,
    val turn: HostBridgeTurnPayload? = null,
    val status: HostBridgeThreadStatusPayload? = null,
    val item: HostBridgeTimelineItemPayload? = null,
    val turnId: String? = null,
    val itemId: String? = null,
    val delta: String? = null,
    val requestId: String? = null,
    val approval: HostBridgeApprovalPayload? = null,
    val questionCount: Int? = null,
    val message: String? = null,
)

interface HostBridgeEventStream : Closeable {
    fun nextEvent(): HostBridgeThreadEvent?
}

class OkHttpHostBridgeClient(
    private val okHttpClient: OkHttpClient = defaultHttpClient(),
) : HostBridgeClient {
    override fun fetchRuntimeSummary(pairingPayload: PairingPayload): HostBridgeRuntimePayload {
        val payload = executeJsonRequest(
            pairingPayload = pairingPayload,
            path = SUMMARY_PATH,
            method = "GET",
            requestBody = null,
        )
        return parseSummaryPayload(
            pairingPayload = pairingPayload,
            payload = payload,
        )
    }

    override fun listThreads(pairingPayload: PairingPayload): HostBridgeThreadListPayload {
        val payload = executeJsonRequest(
            pairingPayload = pairingPayload,
            path = THREAD_LIST_PATH,
            method = "POST",
            requestBody = buildJsonObject { },
        )
        return HostBridgeThreadListPayload(
            data = payload.requireArray("data").toHostBridgeThreadList(),
        )
    }

    override fun readThread(pairingPayload: PairingPayload, threadId: String): HostBridgeThreadPayload {
        val payload = executeJsonRequest(
            pairingPayload = pairingPayload,
            path = "/v1/threads/$threadId",
            method = "GET",
            requestBody = null,
        )
        return payload.requireObject("thread").toHostBridgeThread()
    }

    override fun resumeThread(pairingPayload: PairingPayload, threadId: String): HostBridgeThreadPayload {
        val payload = executeJsonRequest(
            pairingPayload = pairingPayload,
            path = "/v1/threads/$threadId/resume",
            method = "POST",
            requestBody = buildJsonObject { },
        )
        return payload.requireObject("thread").toHostBridgeThread()
    }

    override fun startTurn(
        pairingPayload: PairingPayload,
        threadId: String,
        text: String,
    ): HostBridgeTurnPayload {
        val payload = executeJsonRequest(
            pairingPayload = pairingPayload,
            path = "/v1/threads/$threadId/turns",
            method = "POST",
            requestBody = buildJsonObject {
                put("text", text)
            },
        )
        return payload.requireObject("turn").toHostBridgeTurn()
    }

    override fun interruptTurn(
        pairingPayload: PairingPayload,
        threadId: String,
        turnId: String,
    ) {
        executeJsonRequest(
            pairingPayload = pairingPayload,
            path = "/v1/threads/$threadId/turns/$turnId/interrupt",
            method = "POST",
            requestBody = buildJsonObject { },
        )
    }

    override fun respondToApproval(
        pairingPayload: PairingPayload,
        requestId: String,
        decision: ApprovalDecision,
    ) {
        executeJsonRequest(
            pairingPayload = pairingPayload,
            path = "/v1/approvals/$requestId/respond",
            method = "POST",
            requestBody = buildJsonObject {
                put("decision", decision.toWireDecision())
            },
        )
    }

    override fun openThreadEventStream(
        pairingPayload: PairingPayload,
        threadId: String,
    ): HostBridgeEventStream {
        val streamClient = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(url(pairingPayload.endpoint, "/v1/threads/$threadId/events"))
            .get()
            .header("Authorization", "Bearer ${pairingPayload.sessionToken}")
            .header("Accept", "text/event-stream")
            .build()
        try {
            val response = streamClient.newCall(request).execute()
            val body = response.body ?: run {
                response.close()
                throw HostBridgeClientException(
                    failureCode = HostBridgeClientFailureCode.Protocol,
                    message = "Host Bridge helper не прислал тело SSE stream.",
                )
            }
            if (!response.isSuccessful) {
                val bodyText = body.string()
                response.close()
                throw mapFailure(
                    pairingPayload = pairingPayload,
                    statusCode = response.code,
                    bodyText = bodyText,
                )
            }
            return OkHttpHostBridgeEventStream(
                pairingPayload = pairingPayload,
                response = response,
                source = body.source(),
            )
        } catch (error: HostBridgeClientException) {
            throw error
        } catch (error: IOException) {
            throw HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Unavailable,
                message = error.message ?: "Host Bridge helper недоступен.",
            )
        }
    }

    private fun executeJsonRequest(
        pairingPayload: PairingPayload,
        path: String,
        method: String,
        requestBody: JsonObject?,
    ): JsonObject {
        val builder = Request.Builder()
            .url(url(pairingPayload.endpoint, path))
            .header("Authorization", "Bearer ${pairingPayload.sessionToken}")
            .header("Accept", "application/json")
        when (method) {
            "GET" -> builder.get()
            "POST" -> {
                val rawBody = requestBody?.toString() ?: "{}"
                builder.post(rawBody.toRequestBody(JSON_MEDIA_TYPE))
            }

            else -> error("Unsupported method: $method")
        }
        try {
            okHttpClient.newCall(builder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw mapFailure(
                        pairingPayload = pairingPayload,
                        statusCode = response.code,
                        bodyText = bodyText,
                    )
                }
                return parseJsonObject(bodyText)
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
        val payload = parseJsonObjectOrNull(bodyText)
        val errorCode = payload?.optStringOrNull("errorCode").orEmpty().trim().lowercase()
        val errorMessage = sanitizeRemoteDiagnosticText(
            text = payload?.optStringOrNull("errorMessage"),
            sessionToken = pairingPayload.sessionToken,
        ) ?: "Host Bridge helper вернул HTTP $statusCode."
        return when {
            statusCode == 401 || errorCode == "unauthorized" -> HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Unauthorized,
                message = errorMessage,
            )

            statusCode in 300..399 -> HostBridgeClientException(
                failureCode = HostBridgeClientFailureCode.Protocol,
                message = "Host Bridge helper redirect не разрешен для этого MVP.",
            )

            statusCode in setOf(408, 409, 429, 500, 502, 503, 504) ||
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
        payload: JsonObject,
    ): HostBridgeRuntimePayload = HostBridgeRuntimePayload(
        hostStatus = parseHostStatus(payload.optStringOrNull("hostStatus")),
        runtimeReady = payload.requireBoolean("runtimeReady"),
        appListCount = payload.optIntOrNull("appListCount"),
        lastRoundTripMs = payload.optLongOrNull("lastRoundTripMs"),
        probeAtEpochMs = payload.optLongOrNull("probeAtEpochMs"),
        retryAttempt = payload.optIntOrNull("retryAttempt") ?: 0,
        degradedReason = sanitizeRemoteDiagnosticText(
            text = payload.optStringOrNull("degradedReason"),
            sessionToken = pairingPayload.sessionToken,
        ),
        errorCode = payload.optStringOrNull("errorCode"),
        errorMessage = sanitizeRemoteDiagnosticText(
            text = payload.optStringOrNull("errorMessage"),
            sessionToken = pairingPayload.sessionToken,
        ),
        lastTransportError = sanitizeRemoteDiagnosticText(
            text = payload.optStringOrNull("lastTransportError"),
            sessionToken = pairingPayload.sessionToken,
        ),
    )

    private fun parseHostStatus(rawValue: String?): HostBridgeClientStatus = when (rawValue?.trim()?.lowercase()) {
        "ready" -> HostBridgeClientStatus.Ready
        "degraded" -> HostBridgeClientStatus.Degraded
        "unauthorized" -> HostBridgeClientStatus.Unauthorized
        "unreachable" -> HostBridgeClientStatus.Unreachable
        null, "" -> HostBridgeClientStatus.Unknown
        else -> HostBridgeClientStatus.Unknown
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SUMMARY_PATH = "/v1/runtime/summary"
        const val THREAD_LIST_PATH = "/v1/threads/list"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

private class OkHttpHostBridgeEventStream(
    private val pairingPayload: PairingPayload,
    private val response: okhttp3.Response,
    private val source: BufferedSource,
) : HostBridgeEventStream {
    override fun nextEvent(): HostBridgeThreadEvent? {
        val payload = StringBuilder()
        while (true) {
            val line = try {
                source.readUtf8Line()
            } catch (error: IOException) {
                throw HostBridgeClientException(
                    failureCode = HostBridgeClientFailureCode.Unavailable,
                    message = error.message ?: "SSE stream к Host Bridge helper оборван.",
                )
            } ?: return null
            if (line.isEmpty()) {
                if (payload.isEmpty()) {
                    continue
                }
                val eventPayload = parseJsonObject(payload.toString())
                return eventPayload.toHostBridgeThreadEvent(pairingPayload)
            }
            if (line.startsWith(":")) {
                continue
            }
            if (line.startsWith("data:")) {
                payload.append(line.removePrefix("data:").trimStart())
            }
        }
    }

    override fun close() {
        response.close()
    }
}

private fun url(endpoint: String, path: String): String = endpoint.trimEnd('/') + path

private fun parseJsonObject(rawBody: String): JsonObject = try {
    JSON.decodeFromString<JsonElement>(rawBody).jsonObject
} catch (_: Exception) {
    throw HostBridgeClientException(
        failureCode = HostBridgeClientFailureCode.Protocol,
        message = "Host Bridge helper вернул некорректный JSON.",
    )
}

private fun parseJsonObjectOrNull(rawBody: String): JsonObject? = runCatching {
    parseJsonObject(rawBody)
}.getOrNull()

private fun JsonObject.requireBoolean(name: String): Boolean {
    val value = this[name] ?: JsonNull
    if (value == JsonNull) {
        throw HostBridgeClientException(
            failureCode = HostBridgeClientFailureCode.Protocol,
            message = "Host Bridge helper не прислал корректное поле $name.",
        )
    }
    return value.jsonPrimitive.boolean
}

private fun JsonObject.requireObject(name: String): JsonObject = this[name]?.jsonObject ?: throw HostBridgeClientException(
    failureCode = HostBridgeClientFailureCode.Protocol,
    message = "Host Bridge helper не прислал корректный объект $name.",
)

private fun JsonObject.requireArray(name: String): JsonArray = this[name]?.jsonArray ?: throw HostBridgeClientException(
    failureCode = HostBridgeClientFailureCode.Protocol,
    message = "Host Bridge helper не прислал корректный массив $name.",
)

private fun JsonObject.optStringOrNull(name: String): String? = this[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.optIntOrNull(name: String): Int? = this[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull

private fun JsonObject.optLongOrNull(name: String): Long? = this[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull

private fun JsonObject.optBooleanOrDefault(name: String, defaultValue: Boolean): Boolean =
    this[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: defaultValue

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

private fun JsonArray.toHostBridgeThreadList(): List<HostBridgeThreadPayload> = buildList {
    for (index in indices) {
        add(this@toHostBridgeThreadList[index].jsonObject.toHostBridgeThread())
    }
}

private fun JsonObject.toHostBridgeThread(): HostBridgeThreadPayload = HostBridgeThreadPayload(
    id = requireString("id"),
    cwd = optStringOrNull("cwd").orEmpty(),
    title = optStringOrNull("title").orEmpty(),
    preview = optStringOrNull("preview").orEmpty(),
    sourceKind = optStringOrNull("sourceKind") ?: "unknown",
    updatedAtEpochMs = optLongOrNull("updatedAtEpochMs"),
    createdAtEpochMs = optLongOrNull("createdAtEpochMs"),
    turnCount = optIntOrNull("turnCount") ?: 0,
    status = requireObject("status").toHostBridgeThreadStatus(),
    timeline = optArrayOrNull("timeline")?.toHostBridgeTimeline() ?: emptyList(),
)

private fun JsonObject.toHostBridgeThreadStatus(): HostBridgeThreadStatusPayload = HostBridgeThreadStatusPayload(
    type = optStringOrNull("type") ?: "notLoaded",
    activeFlags = optArrayOrNull("activeFlags")?.toStringList() ?: emptyList(),
)

private fun JsonObject.toHostBridgeTurn(): HostBridgeTurnPayload = HostBridgeTurnPayload(
    id = requireString("id"),
    status = optStringOrNull("status") ?: "inProgress",
    startedAtEpochMs = optLongOrNull("startedAtEpochMs"),
    completedAtEpochMs = optLongOrNull("completedAtEpochMs"),
    durationMs = optLongOrNull("durationMs"),
    errorMessage = optStringOrNull("errorMessage"),
)

private fun JsonArray.toHostBridgeTimeline(): List<HostBridgeTimelineItemPayload> = buildList {
    for (index in indices) {
        add(this@toHostBridgeTimeline[index].jsonObject.toHostBridgeTimelineItem())
    }
}

private fun JsonObject.toHostBridgeTimelineItem(): HostBridgeTimelineItemPayload = HostBridgeTimelineItemPayload(
    type = optStringOrNull("type") ?: "statusEvent",
    id = requireString("id"),
    threadId = requireString("threadId"),
    turnId = optStringOrNull("turnId"),
    itemId = optStringOrNull("itemId"),
    text = optStringOrNull("text"),
    streaming = optBooleanOrDefault("streaming", false),
    phase = optStringOrNull("phase"),
    commandPreview = optStringOrNull("commandPreview"),
    status = optStringOrNull("status"),
    cwd = optStringOrNull("cwd"),
    aggregatedOutput = optStringOrNull("aggregatedOutput"),
    exitCode = optIntOrNull("exitCode"),
    path = optStringOrNull("path"),
    changeKind = optStringOrNull("changeKind"),
    title = optStringOrNull("title"),
    detail = optStringOrNull("detail"),
)

private fun JsonObject.toHostBridgeApproval(): HostBridgeApprovalPayload = HostBridgeApprovalPayload(
    id = requireString("id"),
    requestId = requireString("requestId"),
    itemId = requireString("itemId"),
    threadId = requireString("threadId"),
    turnId = requireString("turnId"),
    kind = optStringOrNull("kind") ?: "command",
    title = optStringOrNull("title").orEmpty(),
    description = optStringOrNull("description").orEmpty(),
    availableDecisions = optArrayOrNull("availableDecisions")?.toStringList() ?: emptyList(),
    command = optStringOrNull("command"),
    cwd = optStringOrNull("cwd"),
    grantRoot = optStringOrNull("grantRoot"),
    networkHost = optStringOrNull("networkHost"),
    networkProtocol = optStringOrNull("networkProtocol"),
    reason = optStringOrNull("reason"),
)

private fun JsonObject.toHostBridgeThreadEvent(pairingPayload: PairingPayload): HostBridgeThreadEvent = HostBridgeThreadEvent(
    method = requireString("method"),
    threadId = optStringOrNull("threadId").orEmpty(),
    turn = optObjectOrNull("turn")?.toHostBridgeTurn(),
    status = optObjectOrNull("status")?.toHostBridgeThreadStatus(),
    item = optObjectOrNull("item")?.toHostBridgeTimelineItem(),
    turnId = optStringOrNull("turnId"),
    itemId = optStringOrNull("itemId"),
    delta = sanitizeRemoteDiagnosticText(optStringOrNull("delta"), pairingPayload.sessionToken),
    requestId = optStringOrNull("requestId"),
    approval = optObjectOrNull("approval")?.toHostBridgeApproval(),
    questionCount = optIntOrNull("questionCount"),
    message = sanitizeRemoteDiagnosticText(optStringOrNull("message"), pairingPayload.sessionToken),
)

private fun JsonObject.requireString(name: String): String = optStringOrNull(name) ?: throw HostBridgeClientException(
    failureCode = HostBridgeClientFailureCode.Protocol,
    message = "Host Bridge helper не прислал корректное поле $name.",
)

private fun JsonObject.optObjectOrNull(name: String): JsonObject? = this[name]?.takeIf { it !is JsonNull }?.jsonObject

private fun JsonObject.optArrayOrNull(name: String): JsonArray? = this[name]?.takeIf { it !is JsonNull }?.jsonArray

private fun JsonArray.toStringList(): List<String> = buildList {
    for (element in this@toStringList) {
        val value = element.jsonPrimitive.contentOrNull
        if (value != null) {
            add(value)
        }
    }
}

private fun ApprovalDecision.toWireDecision(): String = when (this) {
    ApprovalDecision.AcceptOnce -> "accept"
    ApprovalDecision.AcceptSession -> "acceptForSession"
    ApprovalDecision.Decline -> "decline"
    ApprovalDecision.Cancel -> "cancel"
}

private val AUTHORIZATION_BEARER_PATTERN = Regex("(?i)authorization\\s*:\\s*bearer\\s+\\S+")
private val BARE_BEARER_PATTERN = Regex("(?i)bearer\\s+\\S+")
private val JSON = Json {
    ignoreUnknownKeys = true
}
