package dev.vitalcc.stukay.core.logging

import java.time.Instant

fun logEvent(
    area: LogArea,
    eventName: String,
    messageHuman: String,
    fields: Map<String, String> = emptyMap(),
    sessionId: String? = null,
    taskId: String? = null,
    correlationId: String? = null,
    requestId: String? = null,
): LogEvent = LogEvent(
    timestampUtc = Instant.now(),
    level = LogLevel.Info,
    area = area,
    eventName = eventName,
    messageHuman = messageHuman,
    fields = fields,
    sessionId = sessionId,
    taskId = taskId,
    correlationId = correlationId,
    requestId = requestId,
)
